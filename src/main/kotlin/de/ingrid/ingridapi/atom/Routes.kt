package de.ingrid.ingridapi.atom

import de.ingrid.ingridapi.atom.render.AtomBuilders
import de.ingrid.ingridapi.atom.service.AtomUrlBuilder
import de.ingrid.ingridapi.atom.service.DatasetFeedRequest
import de.ingrid.ingridapi.atom.service.DatasetFeedProducer
import de.ingrid.ingridapi.atom.service.OpensearchDescriptionProducer
import de.ingrid.ingridapi.atom.service.ServiceFeedProducer
import de.ingrid.ingridapi.atom.service.ServiceFeedListRequest
import de.ingrid.ingridapi.atom.service.ServiceFeedRequest
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
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.html.*

private const val ATOM_CONTENT_TYPE = "application/atom+xml"
private const val OPENSEARCH_DESCRIPTION_CONTENT_TYPE = "application/opensearchdescription+xml"

fun Application.configureAtomRouting() {
    val root =
        environment.config
            .propertyOrNull("ktor.deployment.rootPath")
            ?.getString()
            ?.trimEnd('/') ?: ""

    routing {
        route("atom", { specName = "atom" }) {
            // Landing page at '/atom' and '/atom/'
            get {
                handleLandingPage(call, root)
            }
            get("/", {
                defaultResponseSpec()
            }) {
                handleLandingPage(call, root)
            }
            get("", {
                description = "The landing page of this ATOM Download Service."
                hidden = true
                defaultResponseSpec()
            }) {}

            // Serve the OpenAPI JSON for the ATOM Download Service at '/atom/api'
            route("api") { openApi("atom") }

            // Swagger UI for the ATOM Download Service at '/atom/swagger'
            route("swagger") {
                swaggerUI("$root/atom/api")
            }

            // The service feed list at '/atom/service-list'
            get("service-list", {
                description =
                    "The Atom feed listing all download services of the InGrid data space, optionally " +
                        "filtered by an InGrid query string. This corresponds to the " +
                        "'Get Service Feed List Operation' of the INSPIRE ATOM Download Service."
                request {
                    queryParameter<String>("q") {
                        description = "An optional InGrid query string to restrict the listed download services."
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The service feed list as Atom feed"
                    }
                }
            }) {
                val urlBuilder = AtomUrlBuilder(requestBaseUrl(call, root))
                val request = ServiceFeedListRequest.fromCall(call)
                val serviceFeedList = dependencies.resolve<ServiceFeedProducer>().produceList(request, urlBuilder)
                if (serviceFeedList.entries.isEmpty()) {
                    call.respondText("No service feeds found.", ContentType.Text.Plain, HttpStatusCode.NotFound)
                    return@get
                }
                call.respondText(AtomBuilders.buildServiceFeedList(serviceFeedList), ContentType.parse(ATOM_CONTENT_TYPE))
            }

            // The service feed at '/atom/service/{uuid}'
            get("service/{uuid}", {
                description =
                    "The Atom feed of the download service with the given uuid, listing its coupled datasets " +
                        "with links to their dataset feeds. This corresponds to the " +
                        "'Get Service Feed Operation' of the INSPIRE ATOM Download Service."
                request {
                    pathParameter<String>("uuid") {
                        description = "The uuid of the download service."
                    }
                    queryParameter<String>("q") {
                        description = "An optional InGrid query string to restrict the listed datasets."
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The service feed as Atom feed"
                    }
                    HttpStatusCode.NotFound to {
                        description = "Service Feed not found"
                    }
                }
            }) {
                val urlBuilder = AtomUrlBuilder(requestBaseUrl(call, root))
                val request = ServiceFeedRequest.fromCall(call)
                val serviceFeed = dependencies.resolve<ServiceFeedProducer>().produce(request, urlBuilder)
                if (serviceFeed == null) {
                    call.respondText(
                        "Service Feed not found: ${request.uuid}",
                        ContentType.Text.Plain,
                        HttpStatusCode.NotFound,
                    )
                    return@get
                }
                call.respondText(AtomBuilders.buildServiceFeed(serviceFeed), ContentType.parse(ATOM_CONTENT_TYPE))
            }

            // The dataset feed at '/atom/dataset/{uuid}'
            get("dataset/{uuid}", {
                description =
                    "The 'Dataset Feed' of a dataset of the download service with the given uuid, describing " +
                        "the downloadable files of the dataset. This corresponds to the " +
                        "'Describe Spatial Data Set Operation' of the INSPIRE ATOM Download Service."
                request {
                    pathParameter<String>("uuid") {
                        description = "The uuid of the download service."
                    }
                    queryParameter<String>("datasetUuid") {
                        description = "The uuid of the dataset."
                    }
                    queryParameter<String>("spatial_dataset_identifier_code") {
                        description =
                            "The code part of the INSPIRE spatial dataset identifier, used when no 'datasetUuid' " +
                                "is given."
                    }
                    queryParameter<String>("spatial_dataset_identifier_namespace") {
                        description = "The namespace part of the INSPIRE spatial dataset identifier."
                    }
                    queryParameter<String>("crs") {
                        description = "The requested coordinate reference system."
                    }
                    queryParameter<String>("language") {
                        description = "The requested language of the dataset."
                    }
                    queryParameter<Boolean>("detail") {
                        description = "Whether a link to the portal detail page shall be added ('true')."
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The dataset feed as Atom feed"
                    }
                    HttpStatusCode.NotFound to {
                        description = "Dataset Feed not found"
                    }
                }
            }) {
                val producer = dependencies.resolve<DatasetFeedProducer>()
                handleDatasetFeed(call, redirectIfSingleLink = false, producer = producer)
            }

            // The get-dataset endpoint at '/atom/get-dataset/{uuid}'
            get("get-dataset/{uuid}", {
                description =
                    "The 'Get Spatial Data Set Operation' of the INSPIRE ATOM Download Service: redirects to " +
                        "the download link if the dataset has exactly one, otherwise returns an Atom feed " +
                        "with all download links."
                request {
                    pathParameter<String>("uuid") {
                        description = "The uuid of the download service."
                    }
                    queryParameter<String>("datasetUuid") {
                        description = "The uuid of the dataset."
                    }
                    queryParameter<String>("spatial_dataset_identifier_code") {
                        description = "The code part of the INSPIRE spatial dataset identifier."
                    }
                    queryParameter<String>("spatial_dataset_identifier_namespace") {
                        description = "The namespace part of the INSPIRE spatial dataset identifier."
                    }
                    queryParameter<String>("crs") {
                        description = "The requested coordinate reference system."
                    }
                    queryParameter<String>("language") {
                        description = "The requested language of the dataset."
                    }
                }
                response {
                    HttpStatusCode.Found to {
                        description = "Redirect to the single download link of the dataset"
                    }
                    HttpStatusCode.OK to {
                        description = "The download links of the dataset as Atom feed"
                    }
                    HttpStatusCode.NotFound to {
                        description = "Dataset not found"
                    }
                }
            }) {
                val producer = dependencies.resolve<DatasetFeedProducer>()
                handleDatasetFeed(call, redirectIfSingleLink = true, producer = producer)
            }

            // The OpenSearch Description Document at '/atom/opensearch-description/{uuid}'
            get("opensearch-description/{uuid}", {
                description =
                    "The OpenSearch Description Document of the download service with the given uuid, " +
                        "describing the ATOM download operations of this service."
                request {
                    pathParameter<String>("uuid") {
                        description = "The uuid of the download service."
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The OpenSearch Description Document"
                    }
                    HttpStatusCode.NotFound to {
                        description = "Download service not found"
                    }
                }
            }) {
                val urlBuilder = AtomUrlBuilder(requestBaseUrl(call, root))
                val request = de.ingrid.ingridapi.atom.service.OpensearchDescriptionRequest.fromCall(call)
                val description = dependencies.resolve<OpensearchDescriptionProducer>().produce(request, urlBuilder)
                if (description == null) {
                    call.respondText(
                        "Download service not found: ${request.uuid}",
                        ContentType.Text.Plain,
                        HttpStatusCode.NotFound,
                    )
                    return@get
                }
                call.respondText(
                    AtomBuilders.buildOpensearchDescription(description),
                    ContentType.parse(OPENSEARCH_DESCRIPTION_CONTENT_TYPE),
                )
            }
        }
    }
}

/**
 * Handles the dataset feed and get-dataset endpoints: both produce the dataset feed, but the
 * get-dataset endpoint redirects to the download link if the dataset has exactly one and
 * otherwise renders the feed of the download links instead of the full dataset feed.
 */
private suspend fun handleDatasetFeed(
    call: ApplicationCall,
    redirectIfSingleLink: Boolean,
    producer: DatasetFeedProducer,
) {
    val urlBuilder = AtomUrlBuilder(requestBaseUrl(call))
    val request = DatasetFeedRequest.fromCall(call)
    val datasetFeed = producer.produce(request, urlBuilder)
    if (datasetFeed == null) {
        call.respondText("Dataset not found.", ContentType.Text.Plain, HttpStatusCode.NotFound)
        return
    }

    // check if we only have one download link, if yes, redirect to the download link
    if (redirectIfSingleLink) {
        val entries = datasetFeed.entries
        if (entries.size == 1) {
            val links = entries.first().links
            if (links.size == 1) {
                call.respondRedirect(links.first().href)
                return
            }
        }
    }

    if (redirectIfSingleLink) {
        // if we have more than one download link, create an atom feed with all of them
        call.respondText(AtomBuilders.buildDataset(datasetFeed), ContentType.parse(ATOM_CONTENT_TYPE))
    } else {
        // the dataset feed describes the downloadable files of the dataset
        call.respondText(AtomBuilders.buildDatasetFeed(datasetFeed), ContentType.parse(ATOM_CONTENT_TYPE))
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
            title("ATOM Download Service")
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
                    h1 { +"ATOM Download Service" }
                    p {
                        +"INSPIRE ATOM Download Service interface to the InGrid data space. Lists download "
                        +"services as Atom feeds and provides the download links of their datasets."
                    }
                }
                article(classes = "api-card") {
                    h2 { +"Endpoints" }
                    ul {
                        li {
                            a(href = "$root/atom/swagger") { +"Swagger UI" }
                            p { +"Interactive documentation of this API." }
                        }
                        li {
                            a(href = "$root/atom/service-list") { +"Service Feed List" }
                            p { +"Atom feed listing all download services (optionally filtered by the 'q' parameter)." }
                        }
                        li {
                            a(href = "$root/atom/opensearch-description/{servicefeed-uuid}") { +"OpenSearch Description" }
                            p { +"The OpenSearch Description Document of a download service." }
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

private fun RouteConfig.defaultResponseSpec() {
    response {
        HttpStatusCode.OK to {
            description = "Successful response"
        }
    }
}
