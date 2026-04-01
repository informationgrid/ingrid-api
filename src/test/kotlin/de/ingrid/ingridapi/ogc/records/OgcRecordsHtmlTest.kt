package de.ingrid.ingridapi.ogc.records

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.ElasticsearchService
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsHtmlTest {
    @Test
    fun testConformanceHtml() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { mockk<RecordsService>(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            client.get("/ogc/records/conformance?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Conformance</h1>"), "Should contain title")
                assertTrue(
                    body.contains("http://www.opengis.net/spec/ogcapi-records-1/1.0/conf/core"),
                    "Should contain core conformance class",
                )
            }
        }

    @Test
    fun testAllCollectionsHtml() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "plugdescription" to
                                JsonObject(
                                    mapOf(
                                        "dataSourceName" to JsonPrimitive("test-collection"),
                                        "description" to JsonPrimitive("Test Description"),
                                    ),
                                ),
                        ),
                    ),
                )
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { RecordsService(esMock) }
                }
                configureOgcRecordsRouting()
            }
            client.get("/ogc/records/collections?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Collections</h1>"), "Should contain title")
                assertTrue(body.contains("test-collection"), "Should contain collection id")
                assertTrue(body.contains("Test Description"), "Should contain collection description")
            }
        }

    @Test
    fun testSingleItemHtml() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            coEvery { esMock.getIndexDocument("test-collection", "record-1") } returns
                JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("Record 1"),
                        "description" to JsonPrimitive("Desc 1"),
                    ),
                )
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { RecordsService(esMock) }
                }
                configureOgcRecordsRouting()
            }
            client.get("/ogc/records/collections/test-collection/items/record-1?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Record: Record 1</h1>"), "Should contain record title")
                assertTrue(body.contains("<p>Desc 1</p>"), "Should contain record description")
            }
        }

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

    @Test
    fun testSingleCollectionHtml() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<RecordsService> { mockk<RecordsService>(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }

            // Test HTML representation
            client.get("/ogc/records/collections/test-id?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Test-id</h1>"), "Should contain collection title")
                assertTrue(body.contains("<code>test-id</code>"), "Should contain collection id")
                assertTrue(body.contains("Description for collection &#39;test-id&#39;"), "Should contain description")
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id?f=html\""),
                    "Should contain link to self",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id?f=json\""),
                    "Should contain link to json representation",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items?f=index\""),
                    "Should contain link to items",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id?f=html\""),
                    "Should contain link to self (html)",
                )
            }

            // Test HTML representation (default)
            client.get("/ogc/records/collections/test-id").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Test-id</h1>"), "Should contain collection title")
            }

            // Test JSON representation
            client.get("/ogc/records/collections/test-id?f=json").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText().replace("\\s".toRegex(), "")
                assertTrue(body.contains("\"id\":\"test-id\""), "Should contain id in JSON")
            }
        }

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
                    body.contains("href=\"/ogc/records/collections/test-id/items?f=index\""),
                    "Should contain JSON link",
                )
                /*
                                assertTrue(
                                    body.contains("href=\"/ogc/records/collections/test-id/items?f=iso\""),
                                    "Should contain ISO link",
                                )
                 */
            }
        }

    @Test
    fun testHtmlPaging() =
        testApplication {
            val esService = mockk<ElasticsearchService>(relaxed = true)

            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { esService }
                    provide<RecordsService> { RecordsService(esService) }
                }
                configureOgcRecordsRouting()
            }

            val mockResponseJson =
                """
                {
                  "took": 1,
                  "timed_out": false,
                  "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 25, "relation": "eq" },
                    "max_score": 1.0,
                    "hits": [
                      {
                        "_index": "test-index",
                        "_id": "record-1",
                        "_score": 1.0,
                        "_source": { "title": "Record 1", "description": "Desc 1" },
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
            val mockSearchResponse = json.decodeFromString<SearchResponse>(mockResponseJson)

            coEvery { esService.getIndexDocuments("test-collection", 10, 0) } returns mockSearchResponse

            client.get("/ogc/records/collections/test-collection/items?format=html&limit=10&offset=0").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Items of test-collection"))
                assertTrue(body.contains("Showing 1 - 10 of 25 items"))
                assertTrue(body.contains("Record 1"))
                assertTrue(body.contains("Next &raquo;"))
                assertTrue(body.contains("offset=10"))
                assertTrue(body.contains("limit=10"))
            }
        }
}
