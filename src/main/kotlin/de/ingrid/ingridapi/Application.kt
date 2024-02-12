package de.ingrid.ingridapi

import de.ingrid.ingridapi.api2.configureRouting2
import de.ingrid.ingridapi.plugins.configureKoin
import de.ingrid.ingridapi.portal.configurePortalRouting
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.base() {
    configureKoin()
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                //            isLenient = true // allow unquoated strings (be more liberal)
                //            explicitNulls = true //
            }
        )
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

    // WARNING: for security, do not include this if not behind a reverse proxy
    install(ForwardedHeaders)
    install(XForwardedHeaders)

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
        info { version = "latest" }
        spec("portal") {
            swagger { swaggerUrl = "" }
            info {
                title = "Portal API"
                description = "Example API 1 for testing and demonstration purposes."
            }
        }
    }
    routing { route("/") { get { call.respondText("Available APIs: portal") } } }
}

fun Application.portal() {
    configurePortalRouting()
}

fun Application.module2() {
    configureRouting2()
}
