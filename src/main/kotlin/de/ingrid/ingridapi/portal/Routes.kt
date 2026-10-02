package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.portal.model.Catalog
import de.ingrid.ingridapi.portal.model.CatalogsResult
import de.ingrid.ingridapi.portal.model.ResponseHierarchy
import de.ingrid.ingridapi.portal.services.CatalogService
import io.github.smiley4.ktoropenapi.config.descriptors.ValueExampleDescriptor
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

fun Application.configurePortalRouting() {
    val root =
        environment.config
            .propertyOrNull("ktor.deployment.rootPath")
            ?.getString()
            ?.trimEnd('/') ?: ""
    routing {
        route("portal", { specName = "portal" }) {
            route("api.json") {
                openApi("portal") // api-spec json is served at '/myApi.json'
            }
            swaggerUI("$root/portal/api.json") // swagger-ui is available at '/mySwagger' or '/mySwagger/index.html'
            post("search", {
                description = "Search for datasets using the Elasticsearch query DSL"
                request {
                    body<Any> {
                        description = "Elasticsearch query DSL (JSON)"
                        example(
                            ValueExampleDescriptor(
                                name = "Match all documents",
                                value = mapOf(
                                    "query" to mapOf("match_all" to emptyMap<String, Any>())
                                ),
                                description = "Example Elasticsearch query that matches all documents",
                            ),
                        )
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "Search results"
                        body<SearchResult>()
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid request"
                    }
                }
            }) {
                val elastic = dependencies.resolve<ElasticsearchService>()
                val requestBody = call.receiveText()
                call.respond(elastic.search(requestBody))
            }

            get("catalogs", {
                description = "Get all connected catalogs which have at least one dataset"
                response {
                    HttpStatusCode.OK to {
                        description = "List of catalogs"
                        body<List<Catalog>>()
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid request"
                    }
                }
            }) {
                val elastic = dependencies.resolve<ElasticsearchService>()
                val catalogService = dependencies.resolve<CatalogService>()
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
                response {
                    HttpStatusCode.OK to {
                        description = "Hierarchical structure of the catalog"
                        body<List<ResponseHierarchy>>()
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid request"
                    }
                }
            }) {
                val index = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val parentUuid = call.parameters["parent"]
                val elastic = dependencies.resolve<ElasticsearchService>()
                val catalogService = dependencies.resolve<CatalogService>()
                val response = elastic.search(getHierarchy(index, parentUuid))
                val result = catalogService.convertCatalogHierarchyResponse(response)
                call.respond(result)
            }
        }
    }
}
