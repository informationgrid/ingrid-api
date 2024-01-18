package de.ingrid.ingridapi.api2

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting2() {
    routing {
        route("api2", { specId = "api2" }) {
            get("test", { description = "Test api" }) {
                call.respondText("Test von API 2 :-)")
            }
            get("param/{arg1}", {
                request {
                    pathParameter<String>("arg1") { description = "This is the arg1-parameter" }
                }
            }) {
                call.respondText("Param: ${call.parameters["arg1"]}")
            }
        }
    }
}