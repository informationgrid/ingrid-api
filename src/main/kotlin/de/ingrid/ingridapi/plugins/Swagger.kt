package de.ingrid.ingridapi.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.configureSwagger() {
    install(OpenApi) {
        info { version = "latest" }
        // exclude the static content routes (e.g. the ATOM download client website) from the
        // documentation: the tailcard selector of the Ktor static content routes renders as
        // the path segment "(static-content)"
        pathFilter = { _, path ->
            path.none { it == "(static-content)" }
        }
        spec("portal") {
            info {
                title = "Portal API"
                description = "This API is used by the InGrid Portal to retrieve data."
            }
        }
        spec("ogc-records") {
            info {
                title = "OGC API - Records"
                description = "OGC API Records endpoints as specified by OGC."
            }
        }
        spec("opensearch") {
            info {
                title = "OpenSearch API"
                description = "OpenSearch 1.1 interface to search the InGrid data space. " +
                    "Results are returned as RSS 2.0 with OpenSearch response elements."
            }
        }
        spec("atom") {
            info {
                title = "ATOM Download Service"
                description = "INSPIRE ATOM Download Service interface to the InGrid data space. " +
                    "Lists download services as Atom feeds and provides the download links of their datasets."
            }
        }
    }
}
