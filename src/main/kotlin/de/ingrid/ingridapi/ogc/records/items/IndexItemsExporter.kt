package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IndexItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        // Implementation for INDEX format
        val result = searchResponse?.hits?.hits?.map { it.source } ?: emptyList()
        call.respond(result)
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    ) {
        if (record != null) {
            val recordWithLinks =
                buildJsonObject {
                    record.forEach { (key, value) -> put(key, value) }
                    put(
                        "links",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("rel", "self")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId")
                                    put("type", "application/json")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("rel", "collection")
                                    put("href", "/ogc/records/collections/$catalogId")
                                    put("type", "application/json")
                                },
                            )
                        },
                    )
                }
            call.respond(recordWithLinks)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
