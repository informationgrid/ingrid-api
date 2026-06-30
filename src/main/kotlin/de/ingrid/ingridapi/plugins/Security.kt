@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val log = logger {}

/**
 * BFF session cookie payload. Contains only an opaque, server-side session id —
 * never any tokens. The cookie itself is signed (HMAC-SHA256) by Ktor `Sessions`.
 */
@Serializable
data class UserSession(
    val sid: String,
)

/**
 * Server-side token store entry. Access and refresh tokens stay in the backend
 * and are never exposed to the browser.
 */
data class TokenStoreEntry(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSec: Long,
    val subject: String?,
    val preferredUsername: String?,
)

/**
 * Simple in-memory token store keyed by an opaque session id.
 *
 * NOTE: In a multi-instance deployment this needs to be replaced with a shared
 * store (Redis, database, …). For a single-instance BFF (typical for a small
 * admin GUI behind a single backend) it is sufficient.
 */
object SessionTokenStore {
    private val store = ConcurrentHashMap<String, TokenStoreEntry>()

    fun put(
        sid: String,
        entry: TokenStoreEntry,
    ) {
        store[sid] = entry
    }

    fun get(sid: String): TokenStoreEntry? = store[sid]

    fun remove(sid: String): TokenStoreEntry? = store.remove(sid)
}

private val httpClient: HttpClient by lazy { HttpClient(Apache5) }
private val json = Json { ignoreUnknownKeys = true }

fun Application.security() {
    // AppConfig has a no-arg constructor and is cheap to instantiate; we read it directly
    // because dependencies.resolve<T>() is `suspend` and cannot be invoked from a regular
    // application module function.
    val cfg = AppConfig()
    val root = cfg.rootPath.trimEnd('/')

    if (cfg.authDisabled) {
        log.info { "Authentication is DISABLED via configuration (dev mode)." }
        SessionTokenStore.put(
            "dev-admin",
            TokenStoreEntry(
                accessToken = "dev-access-token",
                refreshToken = null,
                expiresAtEpochSec = System.currentTimeMillis() / 1000 + 3600 * 24 * 365, // 1 year
                subject = "dev-admin",
                preferredUsername = "Development Admin",
            ),
        )
    }

    install(Sessions) {
        cookie<UserSession>("INGRID_ADMIN_SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
            // Keep this disabled for local HTTP-only development; enable in production behind TLS.
            cookie.secure = cfg.sessionSecure
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(hex(cfg.sessionSignKey)))
        }
    }

    install(Authentication) {
        // ---- OAuth2 Authorization Code flow with Keycloak --------------------
        oauth("keycloak-oauth") {
            urlProvider = {
                val scheme = this.request.origin.scheme
                val host = this.request.origin.serverHost
                "$scheme://$host$root/auth/login"
            }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "keycloak",
                    authorizeUrl = cfg.keycloakAuthorizeUrl,
                    accessTokenUrl = cfg.keycloakTokenUrl,
                    requestMethod = io.ktor.http.HttpMethod.Post,
                    clientId = cfg.keycloakClientId,
                    clientSecret = cfg.keycloakClientSecret,
                    defaultScopes = listOf("openid", "profile", "email"),
                )
            }
            client = httpClient
            fallback = { call, cause ->
                log.warn { "Authentication failed: ${cause.message}" }
                val msg =
                    if (cause.message.contains("401") || cause.message.contains("400")) {
                        "Authentifizierung beim Identity-Provider fehlgeschlagen (evtl. ungültige Client-Credentials)."
                    } else {
                        "Anmeldung fehlgeschlagen: ${cause.message}"
                    }
                call.respondRedirect(
                    "$root/admin/error?err=${
                        java.net.URLEncoder.encode(
                            msg,
                            Charsets.UTF_8,
                        )
                    }",
                )
            }
        }

        // ---- Session-based guard used to protect the admin routes ------------
        if (cfg.authDisabled) {
            class DevAuthConfig(name: String?) : AuthenticationProvider.Config(name)
            val provider =
                object : AuthenticationProvider(DevAuthConfig("admin-session")) {
                    override suspend fun onAuthenticate(context: AuthenticationContext) {
                        val session = context.call.sessions.get<UserSession>()
                        if (session == null || session.sid != "dev-admin") {
                            context.call.sessions.set(UserSession(sid = "dev-admin"))
                        }
                        context.principal(UserSession(sid = "dev-admin"))
                    }
                }
            register(provider)
        } else {
            session<UserSession>("admin-session") {
                validate { session ->
                    val entry = SessionTokenStore.get(session.sid) ?: return@validate null
                    val now = System.currentTimeMillis() / 1000
                    val currentEntry =
                        if (entry.expiresAtEpochSec - 30 > now) {
                            entry
                        } else {
                            // Token expired (or close to expiring) — try to refresh server-side.
                            val refreshed = entry.refreshToken?.let { refreshAccessToken(cfg, it) }
                            if (refreshed != null) {
                                SessionTokenStore.put(session.sid, refreshed)
                                refreshed
                            } else {
                                SessionTokenStore.remove(session.sid)
                                null
                            }
                        } ?: return@validate null

                    // Ensure the user still has the required "admin" role.
                    if (!hasAdminRole(currentEntry.accessToken, cfg.keycloakClientId)) {
                        log.warn {
                            "User '${currentEntry.preferredUsername ?: currentEntry.subject}' lost admin role (sid=${
                                session.sid.take(
                                    8,
                                )
                            }…)"
                        }
                        SessionTokenStore.remove(session.sid)
                        null
                    } else {
                        session
                    }
                }
                challenge {
                    // Remember where the user wanted to go, then send them to login.
                    val original = call.request.local.uri
                    call.respondRedirect(
                        "$root/auth/login" + "?return=${
                            java.net.URLEncoder.encode(
                                original,
                                Charsets.UTF_8,
                            )
                        }",
                    )
                }
            }
        }
    }

    routing {
        if (cfg.authDisabled) {
            get("/auth/login") {
                val target = call.request.queryParameters["return"]?.takeIf { it.startsWith("/") } ?: "$root/admin"
                call.respondRedirect(target)
            }
        }

        // /auth/login starts the OAuth2 flow. The `oauth` provider intercepts the
        // request, redirects the browser to Keycloak, and (on callback) re-enters
        // this handler with a populated principal.
        route("/auth/login") {
            authenticate("keycloak-oauth") {
                get {
                    // Reached only after a successful round-trip to Keycloak.
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>() ?: return@get
                    val (sub, username) = parseIdToken(principal.extraParameters["id_token"])

                    // Verify the "admin" client-role from Keycloak before creating a session.
                    if (!hasAdminRole(principal.accessToken, cfg.keycloakClientId)) {
                        log.warn {
                            "User '${username ?: sub ?: "?"}' denied access: missing 'admin' role for client '${cfg.keycloakClientId}'"
                        }
                        call.respondRedirect(
                            "$root/admin/error?err=${
                                java.net.URLEncoder.encode(
                                    "Zugriff verweigert: Sie verfügen nicht über die erforderliche Administrator-Rolle.",
                                    Charsets.UTF_8,
                                )
                            }",
                        )
                        return@get
                    }

                    val sid = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis() / 1000
                    SessionTokenStore.put(
                        sid,
                        TokenStoreEntry(
                            accessToken = principal.accessToken,
                            refreshToken = principal.refreshToken,
                            expiresAtEpochSec = now + (principal.expiresIn.takeIf { it > 0 } ?: 300L),
                            subject = sub,
                            preferredUsername = username,
                        ),
                    )
                    call.sessions.set(UserSession(sid))
                    log.info { "User '${username ?: sub ?: "?"}' logged in (sid=${sid.take(8)}…)" }
                    val target = call.request.queryParameters["return"]?.takeIf { it.startsWith("/") } ?: "$root/admin"
                    call.respondRedirect(target)
                }
            }
        }

        get("/auth/logout") {
            val session = call.sessions.get<UserSession>()
            if (session != null) {
                SessionTokenStore.remove(session.sid)
                call.sessions.clear<UserSession>()
            }
            // Trigger Keycloak RP-initiated logout so the SSO session is killed too.
            val redirect =
                URLBuilder(cfg.keycloakLogoutUrl)
                    .apply {
                        parameters.append("client_id", cfg.keycloakClientId)
                        val scheme = call.request.origin.scheme
                        val host = call.request.origin.serverHost
                        val externalBaseUrl = "$scheme://$host"
                        parameters.append("post_logout_redirect_uri", "$externalBaseUrl$root/admin")
                    }.buildString()
            call.respondRedirect(redirect)
        }
    }
}

private suspend fun refreshAccessToken(
    cfg: AppConfig,
    refreshToken: String,
): TokenStoreEntry? {
    return try {
        val response =
            httpClient.submitForm(
                url = cfg.keycloakTokenUrl,
                formParameters =
                    parameters {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", cfg.keycloakClientId)
                        if (cfg.keycloakClientSecret.isNotBlank()) {
                            append("client_secret", cfg.keycloakClientSecret)
                        }
                    },
            )
        if (!response.status.isSuccess()) {
            log.warn { "Token refresh failed: HTTP ${response.status}" }
            return null
        }
        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        val access = body["access_token"]?.jsonPrimitive?.content ?: return null
        val newRefresh = body["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
        val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 300L
        val (sub, username) = parseIdToken(body["id_token"]?.jsonPrimitive?.content)
        TokenStoreEntry(
            accessToken = access,
            refreshToken = newRefresh,
            expiresAtEpochSec = System.currentTimeMillis() / 1000 + expiresIn,
            subject = sub,
            preferredUsername = username,
        )
    } catch (ex: Exception) {
        log.warn(ex) { "Token refresh threw" }
        null
    }
}

/** Decode the (unverified) JWT payload to read user identity claims. */
private fun parseIdToken(idToken: String?): Pair<String?, String?> {
    val payload = parseJwtPayload(idToken) ?: return null to null
    return try {
        val sub = payload["sub"]?.jsonPrimitive?.content
        val username =
            payload["preferred_username"]?.jsonPrimitive?.content
                ?: payload["email"]?.jsonPrimitive?.content
        sub to username
    } catch (_: Exception) {
        null to null
    }
}

/**
 * Checks if the access token contains the "admin" role for the specified client.
 * Keycloak structure: resource_access.<client_id>.roles = ["admin", ...]
 */
private fun hasAdminRole(
    accessToken: String,
    clientId: String,
): Boolean {
    val payload = parseJwtPayload(accessToken) ?: return false
    return try {
        payload["resource_access"]
            ?.jsonObject
            ?.get(clientId)
            ?.jsonObject
            ?.get("roles")
            ?.jsonArray
            ?.any { it.jsonPrimitive.content == "admin" }
            ?: false
    } catch (_: Exception) {
        false
    }
}

private fun parseJwtPayload(token: String?): JsonObject? {
    if (token.isNullOrBlank()) return null
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val decoded =
            String(
                Base64
                    .getUrlDecoder()
                    .decode(parts[1]),
            )
        json.parseToJsonElement(decoded).jsonObject
    } catch (_: Exception) {
        null
    }
}

/** Convenience accessor: looks up the user's current access token (for downstream API calls). */
fun ApplicationCall.currentAccessToken(): String? {
    val session = sessions.get<UserSession>() ?: return null
    return SessionTokenStore.get(session.sid)?.accessToken
}

/** Convenience accessor: looks up the authenticated user's display name. */
fun ApplicationCall.currentUserDisplayName(): String? {
    val session = sessions.get<UserSession>() ?: return null
    val entry = SessionTokenStore.get(session.sid) ?: return null
    return entry.preferredUsername ?: entry.subject
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
