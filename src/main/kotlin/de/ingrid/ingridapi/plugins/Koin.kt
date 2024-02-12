package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.core.services.ElasticsearchService
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    val elasticHost = environment.config.property("ktor.elasticsearch.host").getString()
    val elasticPort = environment.config.property("ktor.elasticsearch.port").getString().toInt()

    install(Koin) {
        slf4jLogger()
        properties(mapOf("elasticHost" to elasticHost, "elasticPort" to elasticPort))
        modules(appModule)
    }
}

val appModule = module {
    single(createdAtStart = true) {
        ElasticsearchService(getProperty("elasticHost"), getProperty("elasticPort"))
    }
}
