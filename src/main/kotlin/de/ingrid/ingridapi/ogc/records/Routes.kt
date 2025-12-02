package de.ingrid.ingridapi.ogc.records

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
          mapOf(
            "title" to "OGC API - Records",
            "links" to listOf(
              mapOf("rel" to "self", "href" to "/ogc/records", "type" to "application/json"),
              mapOf("rel" to "service-desc", "href" to "/ogc/records/myApi.json", "type" to "application/vnd.oai.openapi+json;version=3.0"),
              mapOf("rel" to "conformance", "href" to "/ogc/records/conformance", "type" to "application/json"),
              mapOf("rel" to "data", "href" to "/ogc/records/collections", "type" to "application/json", "title" to "Collections")
            )
          )
        )
      }

      // Minimal conformance endpoint (placeholder)
      get("conformance", {
        description = "Reports the conformance classes supported by this implementation"
      }) {
        call.respond(
          mapOf(
            "conformsTo" to listOf(
              // Minimal placeholder conformance classes
              "http://www.opengis.net/spec/ogcapi-records-1/1.0/conf/core",
              // Collections from OGC API - Common
              "http://www.opengis.net/spec/ogcapi-common-2/1.0/conf/collections"
            )
          )
        )
      }

      // Collections list (placeholder)
      get("collections", {
        description = "Lists available record collections"
      }) {
        val collections = listOf(
          mapOf(
            "id" to "records",
            "title" to "Records",
            "description" to "A collection of searchable records",
            "itemType" to "record",
            "extent" to mapOf<String, Any>(
              "spatial" to mapOf(
                "bbox" to listOf(listOf(-180, -90, 180, 90)),
                "crs" to "http://www.opengis.net/def/crs/OGC/1.3/CRS84"
              )
            ),
            "links" to listOf(
              mapOf("rel" to "self", "href" to "/ogc/records/collections/records", "type" to "application/json"),
              mapOf("rel" to "items", "href" to "/ogc/records/collections/records/items", "type" to "application/geo+json")
            )
          )
        )
        call.respond(mapOf("collections" to collections))
      }

      // Single collection by id (placeholder)
      get("collections/{collectionId}", {
        description = "Describes a single collection"
        request {
          pathParameter<String>("collectionId") { description = "Collection identifier" }
        }
      }) {
        val id = call.parameters["collectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val collection = mapOf(
          "id" to id,
          "title" to id.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
          "description" to "Description for collection '$id'",
          "itemType" to "record",
          "links" to listOf(
            mapOf("rel" to "self", "href" to "/ogc/records/collections/$id", "type" to "application/json"),
            mapOf("rel" to "items", "href" to "/ogc/records/collections/$id/items", "type" to "application/geo+json")
          )
        )
        call.respond(collection)
      }

      // Items of a collection (FeatureCollection placeholder)
      get("collections/{collectionId}/items", {
        description = "Lists items of the collection as a FeatureCollection (placeholder)"
        request {
          pathParameter<String>("collectionId") { description = "Collection identifier" }
          queryParameter<Int>("limit") { description = "Max number of items to return" }
          queryParameter<Int>("offset") { description = "Start offset for paging" }
        }
      }) {
        val id = call.parameters["collectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        // Placeholder empty FeatureCollection
        call.respond(
          mapOf(
            "type" to "FeatureCollection",
            "name" to id,
            "features" to emptyList<Any>(),
            "links" to listOf(
              mapOf("rel" to "self", "href" to "/ogc/records/collections/$id/items", "type" to "application/geo+json"),
              mapOf("rel" to "collection", "href" to "/ogc/records/collections/$id", "type" to "application/json")
            )
          )
        )
      }
    }
  }
}
