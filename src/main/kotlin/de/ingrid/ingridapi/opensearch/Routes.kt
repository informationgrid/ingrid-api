package de.ingrid.ingridapi.opensearch

import de.ingrid.ingridapi.opensearch.service.DescriptorRenderer
import de.ingrid.ingridapi.opensearch.service.OpenSearchParams
import de.ingrid.ingridapi.opensearch.service.OpenSearchService
import de.ingrid.ingridapi.opensearch.service.RssRenderer
import de.ingrid.ingridapi.opensearch.service.UvpSupport
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.html.*

/** Configuration of the OpenSearch interface, read from the `ktor.opensearch` section. */
internal class OpenSearchConfig(config: io.ktor.server.config.ApplicationConfig) {
    val shortName: String = config.propertyOrNull("ktor.opensearch.shortName")?.getString() ?: "InGrid OpenSearch"
    val description: String =
        config.propertyOrNull("ktor.opensearch.description")?.getString()
            ?: "Here you can search inside the InGrid data space."
    val tags: String = config.propertyOrNull("ktor.opensearch.tags")?.getString() ?: "ingrid opensearch web"
    val contact: String = config.propertyOrNull("ktor.opensearch.contact")?.getString() ?: ""
    val channelDescription: String =
        config.propertyOrNull("ktor.opensearch.channelDescription")?.getString() ?: "Search results"
    val detailsUrl: String = config.propertyOrNull("ktor.opensearch.detailsUrl")?.getString() ?: ""
    val maxHitsPerPage: Int =
        config.propertyOrNull("ktor.opensearch.maxHitsPerPage")?.getString()?.toIntOrNull() ?: 100
}

fun Application.configureOpenSearchRouting() {
    val root =
        environment.config
            .propertyOrNull("ktor.deployment.rootPath")
            ?.getString()
            ?.trimEnd('/') ?: ""
    val config = OpenSearchConfig(environment.config)
    // --- UVP: parse the UVP-specific channel settings ---
    val uvpChannelConfig = UvpSupport.parseChannelConfig(environment.config)

    routing {
        route("opensearch", { specName = "opensearch" }) {
            // Landing page at '/opensearch' and '/opensearch/'
            get {
                handleLandingPage(call, root)
            }
            get("/", {
                defaultParamSpec()
            }) {
                handleLandingPage(call, root)
            }
            get("", {
                description = "The landing page of this OpenSearch API."
                hidden = true
                defaultParamSpec()
            }) {}

            // Serve the OpenAPI JSON for OpenSearch at '/opensearch/api'
            route("api") { openApi("opensearch") }

            // Swagger UI for OpenSearch at '/opensearch/swagger'
            route("swagger") {
                swaggerUI("$root/opensearch/api")
            }

            // The OpenSearch query endpoint at '/opensearch/query'
            get("query", {
                description =
                    "OpenSearch 1.1 query endpoint. Executes an InGrid query string against the Elasticsearch " +
                        "indices of all active catalogs and returns the results as RSS 2.0 with OpenSearch " +
                        "response elements and InGrid extensions. The legacy parameters 'georss', 'detail', " +
                        "'xml', 't', 'docid', 'altdocid' and 'plugid' are accepted for compatibility but ignored."
                request {
                    queryParameter<String>("q") {
                        description =
                            "The InGrid query string, e.g. 'wasser', '\"wind energy\" -solar' or " +
                                "'title:boden datatype:default ranking:score'. Empty query matches all documents."
                    }
                    queryParameter<Int>("p") {
                        description = "The result page, starting at 1. Defaults to 1."
                    }
                    queryParameter<Int>("h") {
                        description = "The number of hits per result page. Defaults to 10, maximum " +
                            "${config.maxHitsPerPage}."
                    }
                    queryParameter<String>("title") {
                        description = "Optional channel title of the RSS response."
                    }
                    queryParameter<String>("format") {
                        description = "Output format: 'rss' (default). 'rdf' (DCAT-AP.DE) is not supported yet."
                    }
                    // --- UVP: legacy parameter of the UVP-Verbund, isolated in UvpSupport and
                    // removed together with it when it is not needed anymore ---
                    queryParameter<Int>("uvp") {
                        description =
                            "Set to 1 to deliver the UVP-specific response format of the UVP-Verbund: " +
                                "no OpenSearch response elements, channel pubDate of the most recent hit and " +
                                "UVP item elements (guid, author, pubDate, uvp:latest-phase)."
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The search result as RSS 2.0 document with OpenSearch elements"
                    }
                    HttpStatusCode.BadRequest to {
                        description = "Invalid parameter"
                    }
                }
            }) {
                val format = call.request.queryParameters["format"] ?: "rss"
                if (format != "rss") {
                    call.respondText(
                        "Unsupported format '$format'. Only 'rss' is supported.",
                        ContentType.Text.Plain,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                val params =
                    OpenSearchParams(
                        query = call.request.queryParameters["q"] ?: "",
                        page = call.request.queryParameters["p"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1,
                        hitsPerPage =
                            (call.request.queryParameters["h"]?.toIntOrNull()?.takeIf { it > 0 } ?: 10)
                                .coerceAtMost(config.maxHitsPerPage),
                        channelTitle = call.request.queryParameters["title"],
                        // --- UVP: activate the UVP-specific response with `uvp=1` (legacy semantics) ---
                        withUvp = (call.request.queryParameters[UvpSupport.REQUEST_PARAM]?.toIntOrNull() ?: 0) == 1,
                    )

                val service = dependencies.resolve<OpenSearchService>()
                val result = service.search(params)
                val baseUrl = requestBaseUrl(call, root)
                val rss =
                    RssRenderer.render(
                        params = params,
                        result = result,
                        baseUrl = baseUrl,
                        selfUrl = baseUrl + call.request.path() + "?" + call.request.queryString(),
                        channelDescription = config.channelDescription,
                        detailsUrlTemplate = config.detailsUrl,
                        uvpChannelConfig = uvpChannelConfig,
                    )
                call.respondText(rss, ContentType.parse("application/rss+xml"))
            }

            // The OpenSearch Description Document at '/opensearch/descriptor'
            get("descriptor", {
                description = "The OpenSearch Description Document (OSDD) of this search interface."
                response {
                    HttpStatusCode.OK to {
                        description = "The OpenSearch Description Document"
                    }
                }
            }) {
                val renderer =
                    DescriptorRenderer(
                        shortName = config.shortName,
                        description = config.description,
                        tags = config.tags,
                        contact = config.contact,
                    )
                call.respondText(
                    renderer.render(requestBaseUrl(call, root)),
                    ContentType.parse("application/opensearchdescription+xml"),
                )
            }
        }
    }
}

private suspend fun handleLandingPage(
    call: ApplicationCall,
    root: String,
) {
    call.respondHtml {
        attributes["data-theme"] = "light"
        head {
            meta(charset = "utf-8")
            meta("viewport", "width=device-width, initial-scale=1")
            title("OpenSearch API")
            unsafe {
                +"""
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css">
                <style>
                    body { background-color: #f4f7f9; }
                    main { padding-top: 3rem; padding-bottom: 3rem; }
                    .api-card { padding: 1.5rem; border-left: 6px solid #1565c0; margin-bottom: 2rem; }
                </style>
                """.trimIndent()
            }
        }
        body {
            main(classes = "container") {
                header {
                    h1 { +"OpenSearch API" }
                    p { +"OpenSearch 1.1 interface to search the InGrid data space. Results are returned as RSS 2.0 with OpenSearch response elements and InGrid extensions." }
                }
                article(classes = "api-card") {
                    h2 { +"Endpoints" }
                    ul {
                        li {
                            a(href = "$root/opensearch/swagger") { +"Swagger UI" }
                            p { +"Interactive documentation of this API." }
                        }
                        li {
                            a(href = "$root/opensearch/descriptor") { +"OpenSearch Description Document" }
                            p { +"The OSDD to register this search engine in browsers and applications." }
                        }
                        li {
                            a(href = "$root/opensearch/query?q=boden&h=10&p=1") { +"Example query" }
                            p { +"Search for 'boden' as RSS 2.0 feed." }
                        }
                    }
                }
            }
        }
    }
}

/** Builds the absolute base URL (scheme, host, optional port, context path) of the request. */
internal fun requestBaseUrl(
    call: ApplicationCall,
    root: String = "",
): String {
    val origin = call.request.origin
    val isDefaultPort =
        (origin.scheme == "https" && origin.serverPort == 443) ||
            (origin.scheme == "http" && (origin.serverPort == 80 || origin.serverPort <= 0))
    val port = if (isDefaultPort) "" else ":${origin.serverPort}"
    return "${origin.scheme}://${origin.serverHost}$port$root"
}

private fun RouteConfig.defaultParamSpec() {
    response {
        HttpStatusCode.OK to {
            description = "Successful response"
        }
        HttpStatusCode.BadRequest to {
            description = "Invalid parameter"
        }
    }
}
