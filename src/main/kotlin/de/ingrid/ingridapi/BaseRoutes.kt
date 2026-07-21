package de.ingrid.ingridapi

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class DebugInfo(
    val request_host: String,
    val request_port: Int,
    val origin_scheme: String,
    val origin_host: String,
    val origin_port: Int,
    val local_scheme: String,
    val local_port: Int,
    val headers: Map<String, List<String>>,
)

fun Application.configureBaseRoutes() {
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
