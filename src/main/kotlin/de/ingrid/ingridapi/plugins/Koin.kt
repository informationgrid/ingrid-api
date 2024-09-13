package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.config.AppConfig
import de.ingrid.ingridapi.core.services.ElasticsearchService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

val appModule =
    module {
        single { AppConfig() }
        single(createdAtStart = true) {
            ElasticsearchService(get())
        }
    }
