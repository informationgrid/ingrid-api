package de.ingrid.ingridapi.plugins

import de.ingrid.ingridapi.config.AppConfig
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.atom.service.AtomSearchService
import de.ingrid.ingridapi.atom.service.DatasetFeedProducer
import de.ingrid.ingridapi.atom.service.OpensearchDescriptionProducer
import de.ingrid.ingridapi.atom.service.ServiceFeedProducer
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.opensearch.service.OpenSearchService
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
        provide(::OpenSearchService)
        provide(::AtomSearchService)
        provide(::ServiceFeedProducer)
        provide(::DatasetFeedProducer)
        provide(::OpensearchDescriptionProducer)
    }
}
