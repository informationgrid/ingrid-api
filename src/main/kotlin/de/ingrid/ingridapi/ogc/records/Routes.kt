@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.export.CollectionSummary
import de.ingrid.ingridapi.ogc.records.export.CollectionsExporterFactory
import de.ingrid.ingridapi.ogc.records.export.parseExportFormat
import de.ingrid.ingridapi.ogc.records.items.ItemExportFormat
import de.ingrid.ingridapi.ogc.records.items.ItemsExporterFactory.create
import de.ingrid.ingridapi.ogc.records.items.parseItemExportFormat
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Link(
    val rel: String,
    val href: String,
    val type: String? = null,
    val title: String? = null,
)

@Serializable
data class LandingPage(
    val title: String,
    val links: List<Link>,
)

@Serializable
data class Conformance(
    val conformsTo: List<String>,
)

@Serializable
data class CollectionDetail(
    val id: String,
    val title: String,
    val description: String,
    val itemType: String,
    val links: List<Link>,
)

@Serializable
data class FeatureCollection(
    val type: String,
    val name: String,
    val features: List<JsonElement>,
    val links: List<Link>,
)

fun Application.configureOgcRecordsRouting() {
    routing {
        // Serve the OpenAPI JSON for OGC Records at '/ogc/records/myApi.json'
        route("ogc/records/myApi.json") { openApi("ogc-records") }

        // Swagger UI for OGC Records is under '/ogc/records' (and '/ogc/records/index.html')
        route("ogc/records", { specName = "ogc-records" }) {
            swaggerUI("myApi.json")

            // OGC landing page (simplified)
            get("", {
                description = "Landing page for OGC API - Records"
            }) {
                call.respond(
                    LandingPage(
                        title = "OGC API - Records",
                        links =
                            listOf(
                                Link(rel = "self", href = "/ogc/records", type = "application/json"),
                                Link(
                                    rel = "service-desc",
                                    href = "/ogc/records/myApi.json",
                                    type = "application/vnd.oai.openapi+json;version=3.0",
                                ),
                                Link(rel = "conformance", href = "/ogc/records/conformance", type = "application/json"),
                                Link(
                                    rel = "data",
                                    href = "/ogc/records/collections",
                                    type = "application/json",
                                    title = "Collections",
                                ),
                            ),
                    ),
                )
            }

            // Minimal conformance endpoint (placeholder)
            get("conformance", {
                description = "Reports the conformance classes supported by this implementation"
            }) {
                call.respond(
                    Conformance(
                        conformsTo =
                            listOf(
                                // Minimal placeholder conformance classes
                                "http://www.opengis.net/spec/ogcapi-records-1/1.0/conf/core",
                                // Collections from OGC API - Common
                                "http://www.opengis.net/spec/ogcapi-common-2/1.0/conf/collections",
                            ),
                    ),
                )
            }

            // Collections list (placeholder)
            get("collections", {
                description = "Lists available record collections"
                request {
                    queryParameter<String>("format") { description = "Output format: json (default) or html" }
                    queryParameter<String>("f") { description = "Alias for 'format'" }
                }
            }) {
                val recordsService = dependencies.resolve<RecordsService>()
                val collections =
                    recordsService
                        .getCollections()
                        .map {
                            val plug = it["plugdescription"] as JsonObject
                            val name = plug["dataSourceName"].asSafeString()
                            val description = plug["dataSourceDescription"].asSafeString()
                            CollectionSummary(
                                id = name,
                                title = description,
                            )
                        }.toSet()
                val fmtParam = call.request.queryParameters["format"] ?: call.request.queryParameters["f"]
                val exporter = CollectionsExporterFactory.create(parseExportFormat(fmtParam))
                exporter.respond(call, collections.toList())
            }

            // Single collection by id (placeholder)
            get("collections/{collectionId}", {
                description = "Describes a single collection"
                request {
                    pathParameter<String>("collectionId") { description = "Collection identifier" }
                    queryParameter<String>("format") { description = "Output format of the collection detail" }
                }
            }) {
                val id = call.parameters["collectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                val collection =
                    CollectionDetail(
                        id = id,
                        title = id.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        description = "Description for collection '$id'",
                        itemType = "record",
                        links =
                            listOf(
                                Link(
                                    rel = "self",
                                    href = "/ogc/records/collections/$id",
                                    type = "application/json",
                                    title = "This collection as JSON",
                                ),
                                Link(
                                    rel = "self",
                                    href = "/ogc/records/collections/$id?f=html",
                                    type = "text/html",
                                    title = "This collection as HTML",
                                ),
                                Link(
                                    rel = "items",
                                    href = "/ogc/records/collections/$id/items",
                                    type = "application/geo+json",
                                    title = "Items of this collection",
                                ),
                            ),
                    )
                val fmtParam = call.request.queryParameters["format"] ?: call.request.queryParameters["f"]
                val exporter = CollectionsExporterFactory.create(parseExportFormat(fmtParam))
                exporter.respond(call, collection)
            }

            // Items of a collection (FeatureCollection placeholder)
            get("collections/{collectionId}/items", {
                description = "Lists items of the collection as a FeatureCollection (placeholder)"
                request {
                    pathParameter<String>("collectionId") { description = "Collection identifier" }
                    queryParameter<Int>("limit") { description = "Max number of items to return" }
                    queryParameter<Int>("offset") { description = "Start offset for paging" }
                    queryParameter<ItemExportFormat>("format") { description = "Output format of the collection items" }
                }
            }) {
                val recordsService = dependencies.resolve<RecordsService>()
                val id = call.parameters["collectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val featureCollection =
                    FeatureCollection(
                        type = "FeatureCollection",
                        name = id,
                        features = emptyList(),
                        links =
                            listOf(
                                Link(
                                    rel = "self",
                                    href = "/ogc/records/collections/$id/items",
                                    type = "application/geo+json",
                                ),
                                Link(
                                    rel = "collection",
                                    href = "/ogc/records/collections/$id",
                                    type = "application/json",
                                ),
                            ),
                    )
                val fmtParam = call.request.queryParameters["format"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val exporter = create(parseItemExportFormat(fmtParam))
                val searchResponse = recordsService.getRecords(id, limit, offset)
                exporter.respond(call, featureCollection, searchResponse, limit, offset)
            }

            // Single item by id
            get("collections/{collectionId}/items/{recordId}", {
                description = "Describes a single item (record) of the collection"
                request {
                    pathParameter<String>("collectionId") { description = "Collection identifier" }
                    pathParameter<String>("recordId") { description = "Record identifier" }
                    queryParameter<ItemExportFormat>("format") { description = "Output format of the record" }
                }
            }) {
                val recordsService = dependencies.resolve<RecordsService>()
                val collectionId = call.parameters["collectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val recordId = call.parameters["recordId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val fmtParam = call.request.queryParameters["format"]

                val exporter = create(parseItemExportFormat(fmtParam))
                val record = recordsService.getRecord(collectionId, recordId)
                exporter.respondSingle(call, record)
            }
        }
    }
}
