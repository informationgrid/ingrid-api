@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.parameters
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.oauth
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.hex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    install(Sessions) {
        cookie<UserSession>("INGRID_ADMIN_SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
            // Keep this disabled for local HTTP-only development; enable in production behind TLS.
            cookie.secure = true
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(hex(cfg.sessionSignKey)))
        }
    }

    install(Authentication) {
        // ---- OAuth2 Authorization Code flow with Keycloak --------------------
        oauth("keycloak-oauth") {
            urlProvider = { cfg.keycloakRedirectUrl }
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
        }

        // ---- Session-based guard used to protect the admin routes ------------
        session<UserSession>("admin-session") {
            validate { session ->
                val entry = SessionTokenStore.get(session.sid) ?: return@validate null
                val now = System.currentTimeMillis() / 1000
                if (entry.expiresAtEpochSec - 30 > now) {
                    return@validate session
                }
                // Token expired (or close to expiring) — try to refresh server-side.
                val refreshed = entry.refreshToken?.let { refreshAccessToken(cfg, it) }
                if (refreshed != null) {
                    SessionTokenStore.put(session.sid, refreshed)
                    session
                } else {
                    SessionTokenStore.remove(session.sid)
                    null
                }
            }
            challenge {
                // Remember where the user wanted to go, then send them to login.
                val original = call.request.local.uri
                call.respondRedirect("/auth/login?return=${java.net.URLEncoder.encode(original, Charsets.UTF_8)}")
            }
        }
    }

    routing {
        // /auth/login starts the OAuth2 flow. The `oauth` provider intercepts the
        // request, redirects the browser to Keycloak, and (on callback) re-enters
        // this handler with a populated principal.
        authenticate("keycloak-oauth") {
            route("/auth/login") {
                get {
                    // Reached only after a successful round-trip to Keycloak.
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    if (principal == null) {
                        call.respondRedirect(
                            "/admin?err=${
                                java.net.URLEncoder.encode(
                                    "Anmeldung fehlgeschlagen.",
                                    Charsets.UTF_8,
                                )
                            }",
                        )
                        return@get
                    }
                    val (sub, username) = parseIdToken(principal.extraParameters["id_token"])
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
                    val target = call.request.queryParameters["return"]?.takeIf { it.startsWith("/") } ?: "/admin"
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
            val baseOrigin = cfg.keycloakRedirectUrl.substringBefore("/auth/")
            val redirect =
                URLBuilder(cfg.keycloakLogoutUrl)
                    .apply {
                        parameters.append("client_id", cfg.keycloakClientId)
                        parameters.append("post_logout_redirect_uri", "$baseOrigin/admin")
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
    if (idToken.isNullOrBlank()) return null to null
    return try {
        val parts = idToken.split(".")
        if (parts.size < 2) return null to null
        val payload =
            String(
                Base64
                    .getUrlDecoder()
                    .decode(parts[1]),
            )
        val obj = json.parseToJsonElement(payload) as JsonObject
        val sub = obj["sub"]?.jsonPrimitive?.content
        val username =
            obj["preferred_username"]?.jsonPrimitive?.content
                ?: obj["email"]?.jsonPrimitive?.content
        sub to username
    } catch (_: Exception) {
        null to null
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
