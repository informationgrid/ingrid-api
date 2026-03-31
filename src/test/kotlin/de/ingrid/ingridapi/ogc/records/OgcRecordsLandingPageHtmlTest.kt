package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsLandingPageHtmlTest {
    @Test
    fun testGetLandingPageHtml() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { mockk<RecordsService>(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }

            // Test HTML representation
            client.get("/ogc/records/?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>OGC API - Records</h1>"), "Should contain title")
                assertTrue(body.contains("<h2>Links</h2>"), "Should contain Links section")

                // Check for styled links
                assertTrue(body.contains("<span class=\"link-rel\">self</span>"), "Should contain styled self link")
                assertTrue(
                    body.contains("<span class=\"link-rel\">alternate</span>"),
                    "Should contain styled alternate link",
                )
                assertTrue(
                    body.contains("<span class=\"link-rel\">service-desc</span>"),
                    "Should contain styled service-desc link",
                )

                assertTrue(body.contains("href=\"/ogc/records/?f=html\""), "Should contain HTML link")
                assertTrue(body.contains("This landing page as HTML"), "Should contain HTML link title")
            }
        }
}
