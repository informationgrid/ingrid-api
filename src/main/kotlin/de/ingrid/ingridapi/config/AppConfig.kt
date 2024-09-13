package de.ingrid.ingridapi.config

import io.ktor.server.config.ApplicationConfig

class AppConfig {
    private val applicationConfiguration: ApplicationConfig = ApplicationConfig("application.yaml")

    val elasticHost: String = applicationConfiguration.property("ktor.elasticsearch.host").getString()
    val elasticPort: Int = applicationConfiguration.property("ktor.elasticsearch.port").getString().toInt()
    val elasticHttps: Boolean = applicationConfiguration.property("ktor.elasticsearch.https").toString().toBoolean()
    val elasticUsername: String = applicationConfiguration.property("ktor.elasticsearch.username").getString()
    val elasticPassword: String = applicationConfiguration.property("ktor.elasticsearch.password").getString()
}
