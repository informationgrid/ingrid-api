package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exporter for INGRID INDEX JSON format (application/vnd.ingrid.index+json)
 * This format returns the raw Elasticsearch index document without transformation.
 */
class IngridIndexItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        // Return the raw search response hits as INGRID INDEX JSON
        // This is similar to INDEX but with the specific content type
        val result = searchResponse?.hits?.hits?.map { it.source } ?: emptyList()
        call.respondText(
            kotlinx.serialization.json.Json.encodeToString(result),
            ContentType.parse("application/vnd.ingrid.index+json"),
        )
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    ) {
        if (record != null) {
            // Return the raw document with links in INGRID INDEX JSON format
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
                                    put("type", "application/vnd.ingrid.index+json")
                                },
                            )
                            // Add alternate links for all supported formats
                            add(
                                buildJsonObject {
                                    put("rel", "alternate")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId?f=html")
                                    put("type", "text/html")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("rel", "alternate")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId?f=json")
                                    put("type", "application/geo+json")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("rel", "alternate")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId?f=xml")
                                    put("type", "application/xml")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("rel", "alternate")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId?f=index")
                                    put("type", "application/json")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("rel", "alternate")
                                    put("href", "/ogc/records/collections/$catalogId/items/$recordId?f=geodcat-xml")
                                    put("type", "application/rdf+xml")
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
                recordWithLinks.toString(),
                ContentType.parse("application/vnd.ingrid.index+json"),
            )
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
