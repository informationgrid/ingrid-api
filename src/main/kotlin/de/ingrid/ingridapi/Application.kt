package de.ingrid.ingridapi

import de.ingrid.ingridapi.api2.configureRouting2
import de.ingrid.ingridapi.ogc.records.configureOgcRecordsRouting
import de.ingrid.ingridapi.plugins.configureCompression
import de.ingrid.ingridapi.plugins.configureCors
import de.ingrid.ingridapi.plugins.configureKoin
import de.ingrid.ingridapi.plugins.configureSerialization
import de.ingrid.ingridapi.plugins.configureStatusPages
import de.ingrid.ingridapi.plugins.configureSwagger
import de.ingrid.ingridapi.portal.configurePortalRouting
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.base() {
    configureKoin()
    configureSerialization()
    configureCompression()
    configureCors()

    // WARNING: for security, do not include this if not behind a reverse proxy
//    install(ForwardedHeaders)
//    install(XForwardedHeaders)

    configureStatusPages()
    configureSwagger()
    configureBaseRoutes()
}

fun Application.portal() {
    configurePortalRouting()
}

fun Application.module2() {
    configureRouting2()
}

fun Application.ogcRecords() {
    configureOgcRecordsRouting()
}
