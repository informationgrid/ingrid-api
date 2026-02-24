package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class IndexItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        records: SearchResponse.Hits?,
    ) {
        // Implementation for INDEX format
        val result = records?.hits?.map { it.source } ?: emptyList()
        call.respond(result)
    }
}
