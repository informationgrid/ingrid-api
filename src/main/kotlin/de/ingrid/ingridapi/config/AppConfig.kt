package de.ingrid.ingridapi.config

import io.ktor.server.config.ApplicationConfig

class AppConfig {
    private val applicationConfiguration: ApplicationConfig = ApplicationConfig("application.yaml")

    val elasticHost: String = applicationConfiguration.property("ktor.elasticsearch.host").getString()
    val elasticPort: Int = applicationConfiguration.property("ktor.elasticsearch.port").getString().toInt()
    val elasticHttps: Boolean = applicationConfiguration.property("ktor.elasticsearch.https").getString().toBoolean()
    val elasticUsername: String = applicationConfiguration.property("ktor.elasticsearch.username").getString()
    val elasticPassword: String = applicationConfiguration.property("ktor.elasticsearch.password").getString()
    val indexPrefix: String = applicationConfiguration.propertyOrNull("ktor.elasticsearch.indexPrefix")?.getString() ?: ""

    // --- Keycloak / OAuth2 (used by the admin GUI BFF) ----------------------
    val keycloakServerUrl: String = applicationConfiguration.property("ktor.keycloak.serverUrl").getString()
    val keycloakInternalServerUrl: String =
        applicationConfiguration.propertyOrNull("ktor.keycloak.internalServerUrl")?.getString()
            ?.takeIf(String::isNotBlank) ?: keycloakServerUrl
    val keycloakRealm: String = applicationConfiguration.property("ktor.keycloak.realm").getString()
    val keycloakClientId: String = applicationConfiguration.property("ktor.keycloak.clientId").getString()
    val keycloakClientSecret: String = applicationConfiguration.property("ktor.keycloak.clientSecret").getString()

    /**
     * Hex string used to sign the session cookie (HMAC-SHA256). Must remain stable across
     * restarts in production, otherwise existing sessions are invalidated.
     */
    val sessionSignKey: String = applicationConfiguration.property("ktor.session.signKey").getString()
    val sessionSecure: Boolean = applicationConfiguration.property("ktor.session.secure").getString().toBoolean()

    val authDisabled: Boolean =
        applicationConfiguration.propertyOrNull("ktor.security.authDisabled")?.getString()?.toBoolean() ?: false

    val rootPath: String = applicationConfiguration.property("ktor.deployment.rootPath").getString()

    // --- ATOM Download Service ----------------------------------------------
    /** URL template of the CSW GetRecordById access to the metadata records (`{uuid}` placeholder). */
    val atomMetadataAccessUrl: String =
        applicationConfiguration.propertyOrNull("ktor.atom.metadataAccessUrl")?.getString() ?: ""

    /** URL of the portal detail page (linked from dataset feeds when `detail=true`). */
    val atomMetadataDetailsUrl: String =
        applicationConfiguration.propertyOrNull("ktor.atom.metadataDetailsUrl")?.getString() ?: ""

    /** Connect timeout in milliseconds for external HTTP calls (nested Atom/XML documents). */
    val atomUrlConnectTimeout: Int =
        applicationConfiguration.propertyOrNull("ktor.atom.urlConnectTimeout")?.getString()?.toIntOrNull() ?: 1000

    /** Read timeout in milliseconds for external HTTP calls (nested Atom/XML documents). */
    val atomUrlReadTimeout: Int =
        applicationConfiguration.propertyOrNull("ktor.atom.urlReadTimeout")?.getString()?.toIntOrNull() ?: 1000

    /** Optional InGrid query string appended to all queries of the ATOM Download Service. */
    val atomQueryExtension: String =
        applicationConfiguration.propertyOrNull("ktor.atom.queryExtension")?.getString() ?: ""

    /** Comma separated list of supported languages of the ATOM Download Service. */
    val atomSupportedLanguages: List<String> =
        applicationConfiguration.propertyOrNull("ktor.atom.supportedLanguages")
            ?.getString()?.split(",")?.map(String::trim)?.filter(String::isNotEmpty) ?: listOf("de")

    val keycloakIssuer: String
        get() = "${keycloakServerUrl.trimEnd('/')}/realms/$keycloakRealm"
    val keycloakInternalIssuer: String
        get() = "${keycloakInternalServerUrl.trimEnd('/')}/realms/$keycloakRealm"
    val keycloakAuthorizeUrl: String get() = "$keycloakIssuer/protocol/openid-connect/auth"
    val keycloakTokenUrl: String get() = "$keycloakInternalIssuer/protocol/openid-connect/token"
    val keycloakLogoutUrl: String get() = "$keycloakIssuer/protocol/openid-connect/logout"
}
