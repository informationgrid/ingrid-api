package de.ingrid.ingridapi.config

import io.ktor.server.config.ApplicationConfig

class AppConfig {
    private val applicationConfiguration: ApplicationConfig = ApplicationConfig("application.yaml")

    val elasticHost: String = applicationConfiguration.property("ktor.elasticsearch.host").getString()
    val elasticPort: Int = applicationConfiguration.property("ktor.elasticsearch.port").getString().toInt()
    val elasticHttps: Boolean = applicationConfiguration.property("ktor.elasticsearch.https").getString().toBoolean()
    val elasticUsername: String = applicationConfiguration.property("ktor.elasticsearch.username").getString()
    val elasticPassword: String = applicationConfiguration.property("ktor.elasticsearch.password").getString()

    // --- Keycloak / OAuth2 (used by the admin GUI BFF) ----------------------
    val keycloakServerUrl: String = applicationConfiguration.property("ktor.keycloak.serverUrl").getString()
    val keycloakRealm: String = applicationConfiguration.property("ktor.keycloak.realm").getString()
    val keycloakClientId: String = applicationConfiguration.property("ktor.keycloak.clientId").getString()
    val keycloakClientSecret: String = applicationConfiguration.property("ktor.keycloak.clientSecret").getString()

    /**
     * Hex string used to sign the session cookie (HMAC-SHA256). Must remain stable across
     * restarts in production, otherwise existing sessions are invalidated.
     */
    val sessionSignKey: String = applicationConfiguration.property("ktor.session.signKey").getString()
    val sessionSecure: Boolean = applicationConfiguration.property("ktor.session.secure").getString().toBoolean()

    val rootPath: String = applicationConfiguration.property("ktor.deployment.rootPath").getString()

    val keycloakIssuer: String
        get() = "${keycloakServerUrl.trimEnd('/')}/realms/$keycloakRealm"
    val keycloakAuthorizeUrl: String get() = "$keycloakIssuer/protocol/openid-connect/auth"
    val keycloakTokenUrl: String get() = "$keycloakIssuer/protocol/openid-connect/token"
    val keycloakLogoutUrl: String get() = "$keycloakIssuer/protocol/openid-connect/logout"
}
