package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OgcRecordsJsonTest {
    @Test
    fun testSwaggerUi() =
        addWrapper { client, _ ->
            client.get("/ogc/records/swagger").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>Swagger UI</title>")
            }
        }

    @Test
    fun testLandingPage() =
        addWrapper { client, _ ->
            client
                .get("/ogc/records/") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), contentType())
                    val body = body<JsonObject>()
                    assertEquals("OGC API - Records", body["title"]?.jsonPrimitive?.content)
                    val links = body["links"]?.jsonArray
                    assertNotNull(links)
                    assertContains(links.map { it.jsonObject["rel"]?.jsonPrimitive?.content }, "self")
                    assertContains(links.map { it.jsonObject["rel"]?.jsonPrimitive?.content }, "conformance")
                    assertContains(links.map { it.jsonObject["rel"]?.jsonPrimitive?.content }, "data")
                }
        }

    @Test
    fun testConformance() =
        addWrapper { client, _ ->
            client
                .get("/ogc/records/conformance") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val body = body<JsonObject>()
                    val conformsTo = body["conformsTo"]?.jsonArray
                    assertNotNull(conformsTo)
                    assertContains(
                        conformsTo.map { it.jsonPrimitive.content },
                        "http://www.opengis.net/spec/ogcapi-records-1/1.0/conf/core",
                    )
                }
        }

    @Test
    fun testCollectionsEmpty() =
        addWrapper { client, esMock ->
            coEvery { esMock.getActiveCatalogs() } returns emptyList()
            client
                .get("/ogc/records/collections") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
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
                            "plugdescription" to
                                JsonObject(
                                    mapOf(
                                        "dataSourceName" to JsonPrimitive("test-title"),
                                        "description" to JsonPrimitive("Test description"),
                                    ),
                                ),
                        ),
                    ),
                )
            client
                .get("/ogc/records/collections") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val body = body<JsonObject>()
                    val collection = body["collections"]!!.jsonArray[0].jsonObject
                    assertContains(collection["id"]!!.jsonPrimitive.content, "test-title")
                    assertContains(collection["title"]!!.jsonPrimitive.content, "Test description")
                }
        }

    @Test
    fun testItems() =
        addWrapper { client, esMock ->
            val mockResponseJson =
                """
                {
                  "took": 1,
                  "timed_out": false,
                  "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 1, "relation": "eq" },
                    "max_score": 1.0,
                    "hits": [
                      {
                        "_index": "test-index",
                        "_id": "record-1",
                        "_score": 1.0,
                        "_source": { "title": "Record 1" },
                        "fields": {},
                        "sort": [],
                        "inner_hits": {},
                        "highlight": {},
                        "_seq_no": 1,
                        "_primary_term": 1,
                        "_version": 1,
                        "_explanation": {},
                        "matched_queries": []
                      }
                    ]
                  },
                  "aggregations": {},
                  "_scroll_id": "",
                  "pit_id": "",
                  "point_in_time_id": "",
                  "suggest": {}
                }
                """.trimIndent()
            val json =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            val mockSearchResponse = json.decodeFromString<com.jillesvangurp.ktsearch.SearchResponse>(mockResponseJson)

            coEvery { esMock.getIndexDocuments("test-collection", any(), any(), any()) } returns mockSearchResponse

            client
                .get("/ogc/records/collections/test-collection/items?f=index") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val body = body<kotlinx.serialization.json.JsonArray>()
                    assertEquals(1, body.size)
                    assertEquals("Record 1", body[0].jsonObject["title"]?.jsonPrimitive?.content)
                }
        }

    @Test
    fun testSingleItem() =
        addWrapper { client, esMock ->
            val mockRecord =
                JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("Record 1"),
                        "description" to JsonPrimitive("Desc 1"),
                    ),
                )
            coEvery { esMock.getIndexDocument("test-collection", "record-1") } returns mockRecord

            client
                .get("/ogc/records/collections/test-collection/items/record-1?f=index") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val body = body<JsonObject>()
                    assertEquals("Record 1", body["title"]?.jsonPrimitive?.content)
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
