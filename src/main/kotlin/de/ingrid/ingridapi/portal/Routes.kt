package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.portal.services.CatalogService
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configurePortalRouting() {
    val elastic by inject<ElasticsearchService>()
    val catalogService by inject<CatalogService>()

    routing {
        route("portal", { specId = "portal" }) {
            post("search", { request { body<String>() } }) {
                call.respond(elastic.search(call.receiveText()))
            }

            get("catalogs") {
                val response = elastic.search(getCatalogsQuery)

                val result = catalogService.convertCatalogsResponse(response)
                call.respond(result.catalogs)
            }

            get("catalogs/{id}/hierarchy{parent?}") {
                val index = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val parentUuid = call.parameters["parent"] ?: ""

                val response = elastic.search(getHierarchy(index, parentUuid))
                val result = catalogService.convertCatalogHierarchyResponse(response)
                call.respond(result)
            }
        }
    }
}
