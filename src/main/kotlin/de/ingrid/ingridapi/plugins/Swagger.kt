package de.ingrid.ingridapi.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.server.application.*

fun Application.configureSwagger() {
  install(OpenApi) {
    info { version = "latest" }
    spec("portal") {
      info {
        title = "Portal API"
        description = "This API is used by the InGrid Portal to retrieve data."
      }
    }
    spec("ogc-records") {
      info {
        title = "OGC API - Records"
        description = "OGC API Records endpoints as specified by OGC."
      }
    }
  }
}
