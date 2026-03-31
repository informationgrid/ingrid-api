package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.total
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
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
        bbox: String?,
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
                val total = searchResponse?.total ?: 0L
                val selfLink = featureCollection.links.find { it.rel == "self" }
                val baseUrl = selfLink?.href ?: ""
                val pagingLinks = createPagingLinks(baseUrl, total, limit, offset, "json", bbox)
                val combinedLinks = featureCollection.links + pagingLinks
                put(
                    "links",
                    kotlinx.serialization.json.JsonArray(
                        combinedLinks.map {
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
                put("numberMatched", total)
                put("numberReturned", features.size)
            }

        call.respondText(
            geoJson.toString(),
            io.ktor.http.ContentType
                .parse("application/geo+json"),
        )
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    ) {
        if (record != null) {
            val feature =
                buildJsonObject {
                    put("type", "Feature")
                    put("id", record["id"] ?: JsonPrimitive(recordId))
                    put("geometry", record["geometry"] ?: JsonPrimitive(null as String?))
                    put("properties", record)
                    put(
                        "links",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("rel", "self")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId")
                                    put("type", "application/geo+json")
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
            call.respondText(
                feature.toString(),
                io.ktor.http.ContentType
                    .parse("application/geo+json"),
            )
        } else {
            call.respond(io.ktor.http.HttpStatusCode.NotFound)
        }
    }
}
