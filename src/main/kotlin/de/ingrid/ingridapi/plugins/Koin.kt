package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.core.services.ElasticsearchService
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    val elasticHost = environment.config.property("ktor.elasticsearch.host").getString()
    val elasticPort =
        environment.config
            .property("ktor.elasticsearch.port")
            .getString()
            .toInt()
    val elasticHttps =
        environment.config
            .property("ktor.elasticsearch.https")
            .getString()
            .toBoolean()
    val elasticUsername = environment.config.property("ktor.elasticsearch.username").getString()
    val elasticPassword = environment.config.property("ktor.elasticsearch.password").getString()

    install(Koin) {
        slf4jLogger()
        properties(
            mapOf(
                "elasticHost" to elasticHost,
                "elasticPort" to elasticPort,
                "elasticHttps" to elasticHttps,
                "elasticUsername" to elasticUsername,
                "elasticPassword" to elasticPassword,
            ),
        )
        modules(appModule)
    }
}

val appModule =
    module {
        single(createdAtStart = true) {
            ElasticsearchService(
                getProperty("elasticHost"),
                getProperty("elasticPort"),
                getProperty("elasticHttps"),
                getProperty("elasticUsername"),
                getProperty("elasticPassword"),
            )
        }
    }
