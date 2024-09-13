package de.ingrid.ingridapi

import de.ingrid.ingridapi.api2.configureRouting2
import de.ingrid.ingridapi.plugins.configureCompression
import de.ingrid.ingridapi.plugins.configureCors
import de.ingrid.ingridapi.plugins.configureKoin
import de.ingrid.ingridapi.plugins.configureSerialization
import de.ingrid.ingridapi.plugins.configureStatusPages
import de.ingrid.ingridapi.plugins.configureSwagger
import de.ingrid.ingridapi.portal.configurePortalRouting
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.base() {
    configureKoin()
    configureSerialization()
    configureCompression()
    configureCors()

    // WARNING: for security, do not include this if not behind a reverse proxy
    install(ForwardedHeaders)
    install(XForwardedHeaders)

    configureStatusPages()
    configureSwagger()
    routing { route("/") { get { call.respondText("Available APIs: portal") } } }
}

fun Application.portal() {
    configurePortalRouting()
}

fun Application.module2() {
    configureRouting2()
}
