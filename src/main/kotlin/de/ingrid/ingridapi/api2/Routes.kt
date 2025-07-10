package de.ingrid.ingridapi.api2

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting2() {
  routing {
    route("api2/myApi.json") {
      openApi("api2") // api-spec json is served at '/myApi.json'
    }
    route("api2", {specName = "api2"}) {
      swaggerUI("myApi.json") // swagger-ui is available at '/mySwagger' or '/mySwagger/index.html'
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
