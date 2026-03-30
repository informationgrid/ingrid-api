package de.ingrid.ingridapi.ogc.records

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsPagingTest {
    @Test
    fun testPagingParameters() =
        testApplication {
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }

            val esService = mockk<ElasticsearchService>(relaxed = true)

            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { esService }
                    provide<RecordsService> { RecordsService(esService) }
                }
                configureOgcRecordsRouting()
            }

            coEvery { esService.getIndexDocuments("test-collection", any(), any()) } returns null
            coEvery { esService.getActiveCatalogs() } returns emptyList()

            // Test explicit paging
            client.get("/ogc/records/collections/test-collection/items?limit=5&offset=10").apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            // Test default paging
            client.get("/ogc/records/collections/test-collection/items").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

    @Test
    fun testGeoJsonPaging() =
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
                        "_source": { "id": "record-1", "title": "Record 1", "description": "Desc 1" },
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

            client.get("/ogc/records/collections/test-collection/items?format=json&limit=10&offset=0").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("\"rel\": \"next\""), "Should contain next link")
                assertTrue(body.contains("offset=10"), "Next link should have offset 10")
                assertTrue(body.contains("limit=10"), "Next link should have limit 10")
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
