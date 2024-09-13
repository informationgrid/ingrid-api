package de.ingrid.ingridapi.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.configureSwagger() {
    install(SwaggerUI) {
        info { version = "latest" }
        spec("portal") {
            swagger { swaggerUrl = "" }
            info {
                title = "Portal API"
                description = "Example API 1 for testing and demonstration purposes."
            }
        }
    }
}
