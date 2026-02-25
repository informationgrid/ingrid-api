package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.config.AppConfig
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.portal.services.CatalogService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide

fun Application.configureDi() {
    dependencies {
        provide(::AppConfig)
        provide(::CatalogService)
        provide(::ElasticsearchService)
        provide(::RecordsService)
    }
}
