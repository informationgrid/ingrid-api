package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.plugins.openApiSpecSource
import de.ingrid.ingridapi.portal.model.Catalog
import de.ingrid.ingridapi.portal.model.ResponseHierarchy
import de.ingrid.ingridapi.portal.services.CatalogService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.configurePortalRouting() {
    routing {
        route("portal") {
            post("search") {
                val elastic = dependencies.resolve<ElasticsearchService>()
                val requestBody = call.receiveText()
                call.respond(elastic.search(requestBody))
            }.describe {
                description = "Search for datasets using the Elasticsearch query DSL"
                requestBody {
                    description = "Elasticsearch query DSL (JSON)"
                    content {
                        schema = JsonSchema(type = JsonType.OBJECT)
                        example(
                            "Match all documents",
                            ExampleObject(
                                description = "Example Elasticsearch query that matches all documents",
                                value =
                                    GenericElement(
                                        mapOf(
                                            "query" to
                                                GenericElement(
                                                    mapOf(
                                                        "match_all" to GenericElement(emptyMap<String, String>()),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        )
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Search results"
                        schema = jsonSchema<SearchResult>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid request"
                    }
                }
            }

            get("catalogs") {
                val elastic = dependencies.resolve<ElasticsearchService>()
                val catalogService = dependencies.resolve<CatalogService>()
                val response = elastic.search(getCatalogsQuery)

                val result = catalogService.convertCatalogsResponse(response)
                call.respond(result.catalogs)
            }.describe {
                description = "Get all connected catalogs which have at least one dataset"
                responses {
                    HttpStatusCode.OK {
                        description = "List of catalogs"
                        schema = jsonSchema<List<Catalog>>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid request"
                    }
                }
            }

            get("catalogs/{id}/hierarchy") {
                val index = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val parentUuid = call.parameters["parent"]
                val elastic = dependencies.resolve<ElasticsearchService>()
                val catalogService = dependencies.resolve<CatalogService>()
                val response = elastic.search(getHierarchy(index, parentUuid))
                val result = catalogService.convertCatalogHierarchyResponse(response)
                call.respond(result)
            }.describe {
                description = "Get the hierarchical structure of the datasets of a catalog"
                parameters {
                    path("id") {
                        description = "The ID of the catalog which represents the index name"
                        schema = jsonSchema<String>()
                    }
                    query("parent") {
                        description = "The UUID of the parent dataset"
                        schema = jsonSchema<String>()
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Hierarchical structure of the catalog"
                        schema = jsonSchema<List<ResponseHierarchy>>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid request"
                    }
                }
            }
        }

        // Swagger UI for the Portal API at '/portal'; the generated OpenAPI specification
        // (restricted to the routes of the 'portal' section) is served at '/portal/api.json'.
        swaggerUI("portal") {
            info =
                OpenApiInfo(
                    title = "Portal API",
                    description = "This API is used by the InGrid Portal to retrieve data.",
                    version = "latest",
                )
            remotePath = "api.json"
            source = openApiSpecSource("/portal")
        }
    }
}
