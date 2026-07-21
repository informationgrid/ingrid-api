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
        val responseText = "Available APIs: portal, ogc/records"
        get("/") { call.respondText(responseText) }
        get("") { call.respondText(responseText) }

        get("/debug-headers") {
            val headers = call.request.headers.toMap()
            val origin = call.request.origin
            val debugInfo =
                DebugInfo(
                    request_host = call.request.host(),
                    request_port = call.request.port(),
                    origin_scheme = origin.scheme,
                    origin_host = origin.serverHost,
                    origin_port = origin.serverPort,
                    local_scheme = call.request.local.scheme,
                    local_port = call.request.local.localPort,
                    headers = headers,
                )
            call.respond(debugInfo)
        }
    }
}
