package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OgcRecordsBboxTest {
    @Test
    fun testBboxParameterPassedToService() =
        addWrapper { client, esMock ->
            val catalogId = "test-catalog"
            val bbox = "10,20,30,40"

            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "plugdescription" to JsonObject(mapOf("dataSourceName" to JsonPrimitive(catalogId))),
                            "linkedIndex" to JsonPrimitive("test-index"),
                        ),
                    ),
                )
            coEvery { esMock.getIndexDocuments(any(), any(), any(), any()) } returns null

            client.get("/ogc/records/collections/$catalogId/items?bbox=$bbox").apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            coVerify { esMock.getIndexDocuments(catalogId, 10, 0, listOf(10.0, 20.0, 30.0, 40.0)) }
        }

    @Test
    fun testBboxParameterPassedToServiceHtml() =
        addWrapper { client, esMock ->
            val catalogId = "test-catalog"
            val bbox = "10,20,30,40"

            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "plugdescription" to
                                JsonObject(
                                    mapOf(
                                        "dataSourceName" to JsonPrimitive(catalogId),
                                        "description" to JsonPrimitive("Test catalog"),
                                    ),
                                ),
                            "linkedIndex" to JsonPrimitive("test-index"),
                        ),
                    ),
                )
            coEvery { esMock.getIndexDocuments(any(), any(), any(), any()) } returns null

            client.get("/ogc/records/collections/$catalogId/items?bbox=$bbox&f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            coVerify { esMock.getIndexDocuments(catalogId, 10, 0, listOf(10.0, 20.0, 30.0, 40.0)) }
        }

    @Test
    fun testBboxParameterInHtmlLinks() =
        addWrapper { client, esMock ->
            val catalogId = "test-catalog"
            val bbox = "10,20,30,40"

            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "plugdescription" to
                                JsonObject(
                                    mapOf(
                                        "dataSourceName" to JsonPrimitive(catalogId),
                                        "description" to JsonPrimitive("Test catalog"),
                                    ),
                                ),
                            "linkedIndex" to JsonPrimitive("test-index"),
                        ),
                    ),
                )
            coEvery { esMock.getIndexDocuments(any(), any(), any(), any()) } returns null

            client.get("/ogc/records/collections/$catalogId/items?bbox=$bbox&f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                // Check if bbox is preserved in links in the HTML body
                assertContains(body, "bbox=$bbox")
            }
        }

    @Test
    fun testInvalidBboxReturnsBadRequest() =
        addWrapper { client, esMock ->
            val catalogId = "test-catalog"

            // Invalid number of coordinates
            client.get("/ogc/records/collections/$catalogId/items?bbox=10,20,30").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }

            // Non-numeric coordinates
            client.get("/ogc/records/collections/$catalogId/items?bbox=10,20,30,abc").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }
        }

    private fun addWrapper(block: suspend ApplicationTestBuilder.(HttpClient, ElasticsearchService) -> Unit) {
        testApplication {
            val esMock = mockkClass(ElasticsearchService::class)
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            application {
                dependencies {
                    provide<RecordsService> {
                        RecordsService(esMock)
                    }
                }
                configureSerialization()
                configureOgcRecordsRouting()
            }

            block(client, esMock)
        }
    }
}
