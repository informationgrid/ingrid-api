package de.ingrid.ingridapi

import de.ingrid.ingridapi.portal.configureRouting1
import de.ingrid.ingridapi.api2.configureRouting2
import de.ingrid.ingridapi.core.services.ElasticsearchService
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
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger


fun main(args: Array<String>): Unit = EngineMain.main(args)

val appModule = module {

    single {
        ElasticsearchService(getProperty("elasticHost"), getProperty("elasticPort"))
    }
}

fun Application.base() {
    val elasticHost = environment.config.property("ktor.elasticsearch.host").getString()
    val elasticPort = environment.config.property("ktor.elasticsearch.port").getString().toInt()

    install(Koin) {
        slf4jLogger()
        properties(
            mapOf(
                "elasticHost" to elasticHost,
                "elasticPort" to elasticPort
            )
        )
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
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
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
        info {
            version = "latest"
        }
//        server {
//            url = "http://0.0.0.0:8080"
//            description = "Development Server"
//        }
        spec("portal") {
            swagger {
                swaggerUrl = ""
            }
            info {
                title = "Portal API"
                description = "Example API 1 for testing and demonstration purposes."
            }
        }
/*
        spec("api2") {
            swagger {
                swaggerUrl = ""
            }
            info {
                title = "Example of API 2"
                description = "Example API 2 for testing and demonstration purposes."
            }
        }
*/
    }
    routing {
        route("/") {
            get { call.respondText("Available APIs: portal") }
        }
    }
}

fun Application.portal() {
    configureRouting1()
}

fun Application.module2() {
    configureRouting2()
}
