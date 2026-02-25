package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonObject

class IndexItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
    ) {
        // Implementation for INDEX format
        val result = searchResponse?.hits?.hits?.map { it.source } ?: emptyList()
        call.respond(result)
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
    ) {
        if (record != null) {
            call.respond(record)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
