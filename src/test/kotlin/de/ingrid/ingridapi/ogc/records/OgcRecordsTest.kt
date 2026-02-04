package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.apply
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OgcRecordsTest {
    @Test
    fun testSwaggerUi() =
        addWrapper { client, _ ->
            client.get("/ogc/records").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>Swagger UI</title>")
            }
        }

    @Test
    fun testCollectionsEmpty() =
        addWrapper { client, esMock ->
            coEvery { esMock.getActiveCatalogs() } returns emptyList()
            client.get("/ogc/records/collections").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(
                    bodyAsText(),
                    """"collections": []""",
                )
            }
        }

    @Test
    fun testCollections() =
        addWrapper { client, esMock ->
            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "indexId" to JsonPrimitive("test-id"),
                            "iPlugName" to JsonPrimitive("Test Title"),
                        ),
                    ),
                )
            client.get("/ogc/records/collections").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                val collection = body["collections"]!!.jsonArray[0].jsonObject
                assertContains(collection["id"]!!.jsonPrimitive.content, "test-id")
                assertContains(collection["title"]!!.jsonPrimitive.content, "Test Title")
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
                dependencies.provide<RecordsService> {
                    RecordsService(esMock)
                }
                configureSerialization()
                configureOgcRecordsRouting()
            }

            block(client, esMock)
        }
    }
}
