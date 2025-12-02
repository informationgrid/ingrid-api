package de.ingrid.ingridapi

import de.ingrid.ingridapi.api2.configureRouting2
import de.ingrid.ingridapi.plugins.*
import de.ingrid.ingridapi.portal.configurePortalRouting
import de.ingrid.ingridapi.ogc.records.configureOgcRecordsRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

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
