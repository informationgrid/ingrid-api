package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.total
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GeoJsonItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
    ) {
        val features =
            searchResponse?.hits?.hits?.map { hit ->
                buildJsonObject {
                    put("type", "Feature")
                    put("id", hit.id)
                    put("geometry", hit.source?.get("geometry") ?: JsonPrimitive(null as String?))
                    put("properties", hit.source ?: buildJsonObject { })
                }
            } ?: emptyList()

        val geoJson =
            buildJsonObject {
                put("type", "FeatureCollection")
                put("features", kotlinx.serialization.json.JsonArray(features))
                put(
                    "links",
                    kotlinx.serialization.json.JsonArray(
                        featureCollection.links.map {
                            buildJsonObject {
                                put("rel", it.rel)
                                put("href", it.href)
                                if (it.type != null) put("type", it.type)
                                if (it.title != null) put("title", it.title)
                            }
                        },
                    ),
                )
                // Add other mandatory fields like numberMatched if available
                val total = searchResponse?.total ?: 0L
                put("numberMatched", total)
                put("numberReturned", features.size)
            }

        call.respond(geoJson)
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
    ) {
        if (record != null) {
            val feature =
                buildJsonObject {
                    put("type", "Feature")
                    put("id", record["id"] ?: JsonPrimitive("unknown"))
                    put("geometry", record["geometry"] ?: JsonPrimitive(null as String?))
                    put("properties", record)
                }
            call.respond(feature)
        } else {
            io.ktor.http.HttpStatusCode.NotFound
        }
    }
}
