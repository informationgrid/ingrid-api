package de.ingrid.ingridapi

import de.ingrid.ingridapi.portal.configureRouting1
import de.ingrid.ingridapi.api2.configureRouting2
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            configureRouting1()
            configureRouting2()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), "Available APIs:")
        }
    }
}
