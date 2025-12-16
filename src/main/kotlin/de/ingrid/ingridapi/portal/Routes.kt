package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.portal.services.CatalogService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configurePortalRouting() {
    val elastic by inject<ElasticsearchService>()
    val catalogService by inject<CatalogService>()

    routing {
        route("portal/myApi.json") {
            openApi("portal") // api-spec json is served at '/myApi.json'
        }
        route("portal", { specName = "portal" }) {
            swaggerUI("myApi.json") // swagger-ui is available at '/mySwagger' or '/mySwagger/index.html'
            post("search", { request { body<String>() } }) {
                call.respond(elastic.search(call.receiveText()))
            }

            get("catalogs", {
                description = "Get all connected catalogs which have at least one dataset"
            }) {
                val response = elastic.search(getCatalogsQuery)

                val result = catalogService.convertCatalogsResponse(response)
                call.respond(result.catalogs)
            }

            get("catalogs/{id}/hierarchy", {
                description = "Get the hierarchical structure of the datasets of a catalog"
                request {
                    pathParameter<String>("id") {
                        description = "The ID of the catalog which represents the index name"
                    }
                    queryParameter<String>("parent") {
                        description = "The UUID of the parent dataset"
                    }
                }
            }) {
                val index = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val parentUuid = call.parameters["parent"]

                val response = elastic.search(getHierarchy(index, parentUuid))
                val result = catalogService.convertCatalogHierarchyResponse(response)
                call.respond(result)
            }
        }
    }
}
