package de.ingrid.ingridapi.api2

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting2() {
    routing {
        route("api2/myApi.json") {
            openApiSpec("api2") // api-spec json is served at '/myApi.json'
        }
        route("api2") {
            swaggerUI("/portal/myApi.json") // swagger-ui is available at '/mySwagger' or '/mySwagger/index.html'
        }
        route("api2", { specId = "api2" }) {
            get("test", { description = "Test api" }) { call.respondText("Test von API 2 :-)") }
            get(
                "param/{arg1}",
                {
                    request {
                        pathParameter<String>("arg1") { description = "This is the arg1-parameter" }
                    }
                }
            ) {
                call.respondText("Param: ${call.parameters["arg1"]}")
            }
        }
    }
}
