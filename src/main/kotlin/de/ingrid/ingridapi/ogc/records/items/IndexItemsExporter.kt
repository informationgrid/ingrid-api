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
            val root =
                call.application.environment.config
                    .propertyOrNull("ktor.deployment.rootPath")
                    ?.getString()
                    ?.trimEnd('/') ?: ""
            val resourcePath = "$root/ogc/records/collections/$catalogId/items/$recordId"
            val recordWithLinks =
                buildJsonObject {
                    record.forEach { (key, value) -> put(key, value) }
                    put(
                        "links",
                        kotlinx.serialization.json.buildJsonArray {
                            // self + alternates for every supported item format
                            ItemExportFormat.entries.forEach { fmt ->
                                add(
                                    buildJsonObject {
                                        put("rel", if (fmt == ItemExportFormat.INGRID_INDEX_JSON) "self" else "alternate")
                                        put("href", "$resourcePath?f=${fmt.paramValue}")
                                        put("type", fmt.mediaType)
                                        put(
                                            "title",
                                            when (fmt) {
                                                ItemExportFormat.HTML -> "This record as HTML"
                                                ItemExportFormat.ISO -> "This record as ISO 19139"
                                                ItemExportFormat.GEOJSON -> "This record as GeoJSON"
                                                ItemExportFormat.INGRID_INDEX_JSON -> "This record as INGRID index document"
                                                ItemExportFormat.GEODCAT_XML -> "This record as GeoDCAT-AP RDF/XML"
                                            },
                                        )
                                    },
                                )
                            }
                            add(
                                buildJsonObject {
                                    put("rel", "collection")
                                    put("href", "$root/ogc/records/collections/$catalogId?f=json")
                                    put("type", "application/json")
                                    put("title", "The collection description")
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
