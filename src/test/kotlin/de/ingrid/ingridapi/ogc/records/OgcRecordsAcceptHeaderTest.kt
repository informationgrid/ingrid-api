package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

class OgcRecordsAcceptHeaderTest {
    @Test
    fun testGetCollectionWithAcceptHeader() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk<ElasticsearchService>(relaxed = true) }
                    provide<RecordsService> { mockk<RecordsService>(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }

            // Test Accept: text/html header
            client
                .get("/ogc/records/collections/test-id") {
                    header(HttpHeaders.Accept, ContentType.Text.Html.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val ct = contentType()
                    assertEquals(
                        ContentType.Text.Html.withCharset(Charsets.UTF_8),
                        ct,
                        "Should return HTML for Accept: text/html",
                    )
                    val body = bodyAsText()
                    assertTrue(body.contains("<h1>Test-id</h1>"), "Should contain collection title in HTML")
                }

            // Test Accept: application/json
            client
                .get("/ogc/records/collections/test-id") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
                }

            // Test Accept: application/xml (not supported for collections)
            client
                .get("/ogc/records/collections/test-id") {
                    header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }

            // Test Accept: image/png (unsupported)
            client
                .get("/ogc/records/collections/test-id") {
                    header(HttpHeaders.Accept, "image/png")
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }
        }

    @Test
    fun testLandingPageWithAcceptHeader() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }

            client
                .get("/ogc/records") {
                    header(HttpHeaders.Accept, ContentType.Text.Html.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Text.Html, contentType()?.withoutParameters())
                    println(bodyAsText())
                    assertTrue(
                        bodyAsText().contains("<title>OGC API - Records</title>"),
                        "Should contain landing page title",
                    )
                }

            // Test Accept: application/json
            client
                .get("/ogc/records") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
                }

            // Test Accept: application/xml (not supported)
            client
                .get("/ogc/records") {
                    header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }

            // Test Accept: image/png (unsupported)
            client
                .get("/ogc/records") {
                    header(HttpHeaders.Accept, "image/png")
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }
        }

    @Test
    fun testCollectionsListWithAcceptHeader() =
        testApplication {
            val recordsService = mockk<RecordsService>(relaxed = true)
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { recordsService }
                }
                configureOgcRecordsRouting()
            }

            client
                .get("/ogc/records/collections") {
                    header(HttpHeaders.Accept, ContentType.Text.Html.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Text.Html, contentType()?.withoutParameters())
                    assertTrue(bodyAsText().contains("<h1>Collections</h1>"))
                }

            // Test Accept: application/json
            client
                .get("/ogc/records/collections") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
                }

            // Test Accept: application/xml (not supported)
            client
                .get("/ogc/records/collections") {
                    header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }

            // Test Accept: image/png (unsupported)
            client
                .get("/ogc/records/collections") {
                    header(HttpHeaders.Accept, "image/png")
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }
        }

    @Test
    fun testItemsWithAcceptHeader() =
        testApplication {
            val recordsService = mockk<RecordsService>(relaxed = true)
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { recordsService }
                }
                configureOgcRecordsRouting()
            }

            client
                .get("/ogc/records/collections/test-id/items") {
                    header(HttpHeaders.Accept, ContentType.Text.Html.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Text.Html, contentType()?.withoutParameters())
                    assertTrue(bodyAsText().contains("Items of test-id"))
                }

            // Test Accept: application/json -> returns application/geo+json
            client
                .get("/ogc/records/collections/test-id/items") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals("application/geo+json", contentType()?.withoutParameters().toString())
                }

            // Test Accept: application/xml (supported for items as ISO)
            client
                .get("/ogc/records/collections/test-id/items") {
                    header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Xml, contentType()?.withoutParameters())
                }

            // Test Accept: image/png (unsupported)
            client
                .get("/ogc/records/collections/test-id/items") {
                    header(HttpHeaders.Accept, "image/png")
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }
        }

    @Test
    fun testSingleItemWithAcceptHeader() =
        testApplication {
            val recordsService = mockk<RecordsService>(relaxed = true)
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { recordsService }
                }
                configureOgcRecordsRouting()
            }

            val idfXml =
                """
                <idf:idfMdMetadata xmlns:idf="http://www.portalu.de/IDF/1.0" uuid="test-uuid" id="test-id">
                    <idf:idfResponsibleParty>
                        <idf:individualName>John Doe</idf:individualName>
                    </idf:idfResponsibleParty>
                </idf:idfMdMetadata>
                """.trimIndent()

            coEvery { recordsService.getRecord("test-id", "record-id") } returns
                JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("Record Title"),
                        "description" to JsonPrimitive("Record Description"),
                        "idf" to JsonPrimitive(idfXml),
                    ),
                )

            client
                .get("/ogc/records/collections/test-id/items/record-id") {
                    header(HttpHeaders.Accept, ContentType.Text.Html.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Text.Html, contentType()?.withoutParameters())
                    // HtmlItemsExporter uses title and description
                    assertTrue(bodyAsText().contains("Record Title"))
                }

            // Test Accept: application/json -> returns application/geo+json
            client
                .get("/ogc/records/collections/test-id/items/record-id") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals("application/geo+json", contentType()?.withoutParameters().toString())
                }

            // Test Accept: application/xml (supported for items as ISO)
            client
                .get("/ogc/records/collections/test-id/items/record-id") {
                    header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Xml, contentType()?.withoutParameters())
                }

            // Test Accept: application/xml (supported for items as JSON)
            client
                .get("/ogc/records/collections/test-id/items/record-id") {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals("application/geo+json", contentType()?.withoutParameters().toString())
                }

            // Test Accept: image/png (unsupported)
            client
                .get("/ogc/records/collections/test-id/items/record-id") {
                    header(HttpHeaders.Accept, "image/png")
                }.apply {
                    assertEquals(HttpStatusCode.NotAcceptable, status)
                }
        }
}
