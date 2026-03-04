package de.ingrid.ingridapi

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureBaseRoutes() {
    routing {
        // Handle both root paths (with and without trailing slash)
        val responseText = "Available APIs: portal, ogc/records"
        get("/") { call.respondText(responseText) }
        get("") { call.respondText(responseText) }
    }
}
