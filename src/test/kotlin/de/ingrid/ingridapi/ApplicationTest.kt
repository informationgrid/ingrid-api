package de.ingrid.ingridapi

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() =
        testApplication {
            application {
                configureBaseRoutes()
            }
            client.get("/").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "Available APIs:")
            }
            client.get("").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "Available APIs:")
            }
        }
}
