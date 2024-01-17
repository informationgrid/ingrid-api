package de.ingrid.ingridapi

import de.ingrid.ingridapi.api1.Api1Service
import de.ingrid.ingridapi.api1.configureRouting1
import de.ingrid.ingridapi.api2.configureRouting2
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger


fun main(args: Array<String>): Unit = EngineMain.main(args)

val appModule = module {
    singleOf(::Api1Service)
}

fun Application.base() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
    install(ContentNegotiation) {
        json()
    }
    install(Compression)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: This Page Was Not Found", status = status)
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            throw cause
        }
    }
    install(SwaggerUI) {
        swagger {
            swaggerUrl = "swagger-ui"
            forwardRoot = true
        }
        info {
            title = "Example API"
            version = "latest"
            description = "Example API for testing and demonstration purposes."
        }
        server {
            url = "http://0.0.0.0:8080"
            description = "Development Server"
        }
        spec("api1") {
            info {
                title = "Example of API 1"
            }
        }
        spec("api2") {
            info {
                title = "Example of API 2"
            }
        }
    }
}

fun Application.module1() {
    configureRouting1()
}

fun Application.module2() {
    configureRouting2()
}
