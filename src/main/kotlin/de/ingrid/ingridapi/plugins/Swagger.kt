package de.ingrid.ingridapi.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.server.application.*

fun Application.configureSwagger() {
  install(OpenApi) {
    info { version = "latest" }
    spec("portal") {
      info {
        title = "Portal API"
        description = "Example API 1 for testing and demonstration purposes."
      }
    }
  }
}
