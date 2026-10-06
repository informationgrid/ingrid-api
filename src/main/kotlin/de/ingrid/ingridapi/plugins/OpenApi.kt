package de.ingrid.ingridapi.plugins

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.path
import io.ktor.server.routing.routingRoot
import io.ktor.server.routing.openapi.OpenApiDocSource

/**
 * All routes at or below [basePath] (e.g. "/portal" or "/ogc/records").
 *
 * Used as the route source when generating a per-section OpenAPI specification.
 */
internal fun Application.routesBelow(basePath: String): Sequence<Route> =
    routingRoot.descendants().filter { it.path == basePath || it.path.startsWith("$basePath/") }

/**
 * OpenAPI document source that generates a JSON specification for all routes below [basePath].
 *
 * The specification is assembled at runtime from the routing tree, so it always reflects the
 * routes that are currently registered.
 */
internal fun Application.openApiSpecSource(basePath: String): OpenApiDocSource =
    OpenApiDocSource.Routing(contentType = ContentType.Application.Json) {
        routesBelow(basePath)
    }

/**
 * Reads the OpenAPI document for all routes below [basePath] from the routing tree.
 */
internal fun Application.openApiDoc(
    basePath: String,
    info: OpenApiInfo,
): OpenApiDocSource.Text =
    requireNotNull(openApiSpecSource(basePath).read(this, OpenApiDoc(info = info))) {
        "Failed to generate OpenAPI specification for routes below '$basePath'"
    }
