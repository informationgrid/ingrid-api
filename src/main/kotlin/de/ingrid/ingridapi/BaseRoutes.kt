package de.ingrid.ingridapi

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureBaseRoutes() {
  routing {
    // Handle both root paths (with and without trailing slash)
    val responseText = "Available APIs: portal"
    get("/") { call.respondText(responseText) }
    get("") { call.respondText(responseText) }
  }
}
