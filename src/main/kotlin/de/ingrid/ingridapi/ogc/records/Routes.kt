@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.export.CollectionSummary
import de.ingrid.ingridapi.ogc.records.export.CollectionsExporterFactory
import de.ingrid.ingridapi.ogc.records.export.ExportFormat
import de.ingrid.ingridapi.ogc.records.export.ExportFormatResult
import de.ingrid.ingridapi.ogc.records.export.LandingPage
import de.ingrid.ingridapi.ogc.records.export.SUPPORTED_COLLECTION_FORMATS
import de.ingrid.ingridapi.ogc.records.export.parseExportFormatResult
import de.ingrid.ingridapi.ogc.records.items.ItemExportFormat
import de.ingrid.ingridapi.ogc.records.items.ItemExportFormatResult
import de.ingrid.ingridapi.ogc.records.items.ItemsExporterFactory.create
import de.ingrid.ingridapi.ogc.records.items.SUPPORTED_ITEM_FORMATS
import de.ingrid.ingridapi.ogc.records.items.parseBboxParam
import de.ingrid.ingridapi.ogc.records.items.parseItemExportFormatResult
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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

/**
 * Build alternate-format links for a given resource path, used in 406 responses
 * to point clients to representations the server can actually produce.
 */
private fun collectionAlternateLinks(resourcePath: String): List<Link> =
    ExportFormat.entries.map {
        Link(
            rel = "alternate",
            type = it.mediaType,
            href = "$resourcePath?f=${it.paramValue}",
        )
    }

private fun itemAlternateLinks(resourcePath: String): List<Link> =
    ItemExportFormat.entries.map {
        Link(
            rel = "alternate",
            type = it.mediaType,
            href = "$resourcePath?f=${it.paramValue}",
        )
    }

/**
 * Resolves the collection export format from query/header inputs.
 * On invalid `f` parameter responds with HTTP 400 (InvalidParameterValue) and returns null.
 * On unsupported Accept header responds with HTTP 406 (NotAcceptable) and returns null.
 */
private suspend fun resolveCollectionFormat(
    call: ApplicationCall,
    root: String,
    resourcePathSuffix: String,
): ExportFormat? {
    val fmtParam = call.request.queryParameters["format"] ?: call.request.queryParameters["f"]
    val accept = call.request.headers[HttpHeaders.Accept]
    return when (val r = parseExportFormatResult(fmtParam, accept)) {
        is ExportFormatResult.Ok -> r.format
        is ExportFormatResult.InvalidParam -> {
            respondInvalidFormatParameter(call, r.value, SUPPORTED_COLLECTION_FORMATS)
            null
        }
        is ExportFormatResult.NotAcceptable -> {
            respondNotAcceptable(call, r.acceptHeader, collectionAlternateLinks("$root$resourcePathSuffix"))
            null
        }
    }
}

private suspend fun resolveItemFormat(
    call: ApplicationCall,
    root: String,
    resourcePathSuffix: String,
): ItemExportFormat? {
    val fmtParam = call.request.queryParameters["format"] ?: call.request.queryParameters["f"]
    val accept = call.request.headers[HttpHeaders.Accept]
    return when (val r = parseItemExportFormatResult(fmtParam, accept)) {
        is ItemExportFormatResult.Ok -> r.format
        is ItemExportFormatResult.InvalidParam -> {
            respondInvalidFormatParameter(call, r.value, SUPPORTED_ITEM_FORMATS)
            null
        }
        is ItemExportFormatResult.NotAcceptable -> {
            respondNotAcceptable(call, r.acceptHeader, itemAlternateLinks("$root$resourcePathSuffix"))
            null
        }
    }
}

private suspend fun handleLandingPage(call: ApplicationCall, root: String) {
    val format = resolveCollectionFormat(call, root, "/ogc/records") ?: return
    val exporter = CollectionsExporterFactory.create(format)
    exporter.respondLandingPage(
        call,
        LandingPage(
            title = "OGC API - Records",
            description = "This is the landing page of the OGC API - Records service.",
            links =
                listOf(
                    Link(
                        rel = "self",
                        href = "$root/ogc/records/?f=json",
                        type = "application/json",
                        title = "This landing page as JSON",
                    ),
                    Link(
                        rel = "alternate",
                        href = "$root/ogc/records/?f=html",
                        type = "text/html",
                        title = "This landing page as HTML",
                    ),
                    Link(
                        rel = "service-desc",
                        href = "$root/ogc/records/api",
                        type = "application/vnd.oai.openapi+json;version=3.0",
                        title = "The OpenAPI definition for this API",
                    ),
                    Link(
                        rel = "service-doc",
                        href = "$root/ogc/records/swagger",
                        type = "text/html",
                        title = "The Swagger UI for this API",
                    ),
                    Link(
                        rel = "conformance",
                        href = "$root/ogc/records/conformance",
                        type = "application/json",
                        title = "Conformance classes supported by this API",
                    ),
                    Link(
                        rel = "data",
                        href = "$root/ogc/records/collections",
                        type = "application/json",
                        title = "Collections provided by this API",
                    ),
                ),
        ),
    )
}

fun Application.configureOgcRecordsRouting() {
    val root = environment.config.propertyOrNull("ktor.deployment.rootPath")?.getString()?.trimEnd('/') ?: ""
    routing {
        // Landing page at '/ogc/records' and '/ogc/records/'
        route("ogc/records", { specName = "ogc-records" }) {
            get {
                handleLandingPage(call, root)
            }
            get("/") {
                handleLandingPage(call, root)
            }
            get("", {
                description = "The landing page of this OGC API."
                hidden = true
                request {
                    queryParameter<String>("format") { description = "Output format: json (default) or html" }
                }
            }) {}

            // Serve the OpenAPI JSON for OGC Records at '/ogc/records/api'
            route("api") { openApi("ogc-records") }

            // Swagger UI for OGC Records at '/ogc/records/swagger'
            route("swagger") {
                swaggerUI("$root/ogc/records/api")
            }

            // Minimal conformance endpoint
            get("conformance", {
                description = "Reports the conformance classes supported by this implementation"
                request {
                    queryParameter<String>("format") { description = "Output format: json (default) or html" }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Successful response"
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid parameter"
                    }
                }
            }) {
                val format = resolveCollectionFormat(call, root, "/ogc/records/conformance") ?: return@get
                val exporter = CollectionsExporterFactory.create(format)
                exporter.respondConformance(
                    call,
                    Conformance(
                        conformsTo =
                            listOf(
                                "http://www.opengis.net/spec/ogcapi-records-1/1.0/conf/core",
                                "http://www.opengis.net/spec/ogcapi-common-2/1.0/conf/collections",
//                                "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson",
                                "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/html",
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
                response {
                    HttpStatusCode.OK to {
                        description = "Successful response"
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid parameter"
                    }
                }
            }) {
                val knownParams = listOf("format", "f")
                if (call.request.queryParameters
                        .names()
                        .any { it !in knownParams }
                ) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                val format = resolveCollectionFormat(call, root, "/ogc/records/collections") ?: return@get
                val recordsService = dependencies.resolve<RecordsService>()
                val collections =
                    recordsService
                        .getCollections()
                        .map {
                            val plug = it["plugdescription"] as JsonObject
                            val id = plug["dataSourceName"].asSafeString()
                            CollectionSummary(
                                id = id,
                                title = plug["description"].asSafeString(),
                                links =
                                    listOf(
                                        Link(
                                            rel = "self",
                                            href = "$root/ogc/records/collections/$id",
                                            type = "application/json",
                                        ),
                                        Link(
                                            rel = "items",
                                            href = "$root/ogc/records/collections/$id/items?f=ingrid-index-json",
                                            type = "application/vnd.ingrid.index+json",
                                            title = "Items of this collection as INGRID index documents",
                                        ),
                                        Link(
                                            rel = "alternate",
                                            href = "$root/ogc/records/collections/$id/items?f=html",
                                            type = "text/html",
                                            title = "Items of this collection as HTML",
                                        ),
                                        Link(
                                            rel = "alternate",
                                            href = "$root/ogc/records/collections/$id/items?f=ingrid-index-json",
                                            type = "application/vnd.ingrid.index+json",
                                            title = "Items of this collection as INGRID index documents",
                                        ),
                                    ),
                            )
                        }.toSet()
                val exporter = CollectionsExporterFactory.create(format)
                val links =
                    listOf(
                        Link(
                            rel = "self",
                            href = "$root/ogc/records/collections?f=json",
                            type = "application/json",
                            title = "This document as JSON",
                        ),
                        Link(
                            rel = "alternate",
                            href = "$root/ogc/records/collections?f=html",
                            type = "text/html",
                            title = "This document as HTML",
                        ),
                    )
                exporter.respond(call, collections.toList(), links)
            }

            // Single collection by id (placeholder)
            get("collections/{catalogId}", {
                description = "Describes a single collection"
                request {
                    pathParameter<String>("catalogId") { description = "Collection identifier" }
                    queryParameter<String>("format") { description = "Output format of the collection detail" }
                }
            }) {
                val id = call.parameters["catalogId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val format = resolveCollectionFormat(call, root, "/ogc/records/collections/$id") ?: return@get

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
                                    href = "$root/ogc/records/collections/$id?f=json",
                                    type = "application/json",
                                    title = "This collection as JSON",
                                ),
                                Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id?f=html",
                                    type = "text/html",
                                    title = "This collection as HTML",
                                ),
                                Link(
                                    rel = "items",
                                    href = "$root/ogc/records/collections/$id/items?f=ingrid-index-json",
                                    type = "application/vnd.ingrid.index+json",
                                    title = "Items of this collection as INGRID index documents",
                                ),
                                Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=html",
                                    type = "text/html",
                                    title = "Items of this collection as HTML",
                                ),
                                /*Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=iso",
                                    type = "application/xml",
                                    title = "Items of this collection as ISO 19139 XML",
                                ),
                                Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=geojson",
                                    type = "application/geo+json",
                                    title = "Items of this collection as GeoJSON documents",
                                ),*/
                            ),
                    )
                val exporter = CollectionsExporterFactory.create(format)
                exporter.respond(call, collection)
            }

            // Items of a collection (FeatureCollection placeholder)
            get("collections/{catalogId}/items", {
                description = "Lists items of the collection as a FeatureCollection (placeholder)"
                request {
                    pathParameter<String>("catalogId") { description = "Collection identifier" }
                    queryParameter<Int>("limit") { description = "Max number of items to return" }
                    queryParameter<Int>("offset") { description = "Start offset for paging" }
                    queryParameter<String>("bbox") { description = "Bounding box: minLon,minLat,maxLon,maxLat" }
                    queryParameter<ItemExportFormat>("format") {
                        description = "Output format of the collection items"
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Successful response"
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid parameter"
                    }
                }
            }) {
                val knownParams = listOf("limit", "offset", "format", "f", "bbox")
                if (call.request.queryParameters
                        .names()
                        .any { it !in knownParams }
                ) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                if (call.request.queryParameters["limit"] != null && limit == null) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                val bboxParam = call.request.queryParameters["bbox"]
                val bbox = parseBboxParam(bboxParam)
                if (bboxParam != null && bbox == null) {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }

                val recordsService = dependencies.resolve<RecordsService>()
                val id = call.parameters["catalogId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val itemFormat = resolveItemFormat(call, root, "/ogc/records/collections/$id/items") ?: return@get
                val featureCollection =
                    FeatureCollection(
                        type = "FeatureCollection",
                        name = id,
                        features = emptyList(),
                        links =
                            listOf(
                                /*Link(
                                    rel = "self",
                                    href = "$root/ogc/records/collections/$id/items?f=json${if (bboxParam != null) "&bbox=$bboxParam" else ""}",
                                    type = "application/geo+json",
                                    title = "Items of this collection as GeoJSON",
                                ),*/
                                Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=html${if (bboxParam != null) "&bbox=$bboxParam" else ""}",
                                    type = "text/html",
                                    title = "Items of this collection as HTML",
                                ),
                                /*Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=iso${if (bboxParam != null) "&bbox=$bboxParam" else ""}",
                                    type = "application/xml",
                                    title = "Items of this collection as ISO 19139 XML",
                                ),*/
                                Link(
                                    rel = "alternate",
                                    href = "$root/ogc/records/collections/$id/items?f=ingrid-index-json${if (bboxParam != null) "&bbox=$bboxParam" else ""}",
                                    type = "application/vnd.ingrid.index+json",
                                    title = "Items of this collection as INGRID index documents",
                                ),
                                Link(
                                    rel = "collection",
                                    href = "$root/ogc/records/collections/$id?f=json",
                                    type = "application/json",
                                    title = "The collection description",
                                ),
                            ),
                    )
                val limitValue = limit ?: 10
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val exporter = create(itemFormat)
                val searchResponse = recordsService.getRecords(id, limitValue, offset, bbox)
                exporter.respond(call, featureCollection, searchResponse, limitValue, offset, bboxParam)
            }

            // Single item by id
            get("collections/{catalogId}/items/{recordId}", {
                description = "Describes a single item (record) of the collection"
                request {
                    pathParameter<String>("catalogId") { description = "Collection identifier" }
                    pathParameter<String>("recordId") { description = "Record identifier" }
                    queryParameter<ItemExportFormat>("format") { description = "Output format of the record" }
                }
            }) {
                val recordsService = dependencies.resolve<RecordsService>()
                val catalogId =
                    call.parameters["catalogId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val recordId = call.parameters["recordId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val itemFormat =
                    resolveItemFormat(call, root, "/ogc/records/collections/$catalogId/items/$recordId") ?: return@get

                val exporter = create(itemFormat)
                val record = recordsService.getRecord(catalogId, recordId)
                exporter.respondSingle(call, record, catalogId, recordId)
            }
        }
    }
}
