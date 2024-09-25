package de.ingrid.ingridapi

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureBaseRoutes() {
    routing { route("/") { get { call.respondText("Available APIs: portal") } } }
}
