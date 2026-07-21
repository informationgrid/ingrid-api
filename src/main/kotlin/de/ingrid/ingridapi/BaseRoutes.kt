package de.ingrid.ingridapi

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*

fun Application.configureBaseRoutes() {
    val root =
        environment.config
            .propertyOrNull("ktor.deployment.rootPath")
            ?.getString()
            ?.trimEnd('/') ?: ""
    routing {
        // Handle both root paths (with and without trailing slash)
        get("/") { call.respondHtml { renderIndexPage(root) } }
        get("") { call.respondHtml { renderIndexPage(root) } }
    }
}
