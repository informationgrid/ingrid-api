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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsItemsHtmlTest {
    @Test
    fun testGetItemsHtml() =
        testApplication {
            val recordsService = mockk<RecordsService>(relaxed = true)
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { recordsService }
                }
                configureOgcRecordsRouting()
            }

            // Test HTML representation of items list
            client.get("/ogc/records/collections/test-id/items?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Items of test-id</h1>"), "Should contain page title")
                assertTrue(body.contains("<h2>Links</h2>"), "Should contain Links section")
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items?f=html\""),
                    "Should contain HTML link",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items?f=json\""),
                    "Should contain JSON link",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items?f=iso\""),
                    "Should contain ISO link",
                )
            }
        }

    @Test
    fun testGetItemHtml() =
        testApplication {
            val recordsService = mockk<RecordsService>()
            coEvery { recordsService.getRecord("test-id", "record-1") } returns
                JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("Test Record"),
                        "description" to JsonPrimitive("Test Description"),
                    ),
                )

            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { recordsService }
                }
                configureOgcRecordsRouting()
            }

            // Test HTML representation of a single item
            client.get("/ogc/records/collections/test-id/items/record-1?format=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Record: Test Record</h1>"), "Should contain record title")
                assertTrue(body.contains("<h2>Links</h2>"), "Should contain Links section")
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items/record-1?format=html\""),
                    "Should contain HTML link",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items/record-1?format=json\""),
                    "Should contain JSON link",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items/record-1?format=iso\""),
                    "Should contain ISO link",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id?format=html\""),
                    "Should contain collection link",
                )
            }
        }
}
