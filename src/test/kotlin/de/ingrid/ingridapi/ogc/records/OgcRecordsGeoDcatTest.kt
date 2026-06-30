package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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

class OgcRecordsGeoDcatTest {
    @Test
    fun testGetRecordGeoDcat() =
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

            val recordJson = JsonObject(mapOf(
                "title" to JsonPrimitive("Test Title"),
                "description" to JsonPrimitive("Test Description"),
                "obj_uuid" to JsonPrimitive("test-uuid-123"),
                "created" to JsonPrimitive("2023-01-01"),
                "modified" to JsonPrimitive("2023-06-23")
            ))

            coEvery { esService.getIndexDocument("test-collection", "record-1") } returns recordJson

            client.get("/ogc/records/collections/test-collection/items/record-1?f=geodcat").apply {
                val body = bodyAsText()
                assertEquals(HttpStatusCode.OK, status, "Status should be OK. Body: $body")
                assertTrue(body.contains("xmlns:dcat=\"http://www.w3.org/ns/dcat#\""), "Should contain DCAT namespace")
                assertTrue(body.contains("xmlns:dct=\"http://purl.org/dc/terms/\""), "Should contain DCTerms namespace")
                assertTrue(body.contains("rdf:about=\"/ogc/records/collections/test-collection/items/record-1\""), "Should contain about URI")
                assertTrue(body.contains("<dct:title>Test Title</dct:title>"), "Should contain title")
                assertTrue(body.contains("<dct:description>Test Description</dct:description>"), "Should contain description")
                assertTrue(body.contains("<dct:identifier>test-uuid-123</dct:identifier>"), "Should contain identifier")
//                assertTrue(body.contains("<dct:issued>2023-01-01</dct:issued>"), "Should contain issued date")
                assertTrue(body.contains("<dct:modified>2023-06-23</dct:modified>"), "Should contain modified date")
            }
        }

    @Test
    fun testGetCollectionGeoDcat() =
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

            val mockResponseJson = """
                {
                  "took": 1,
                  "timed_out": false,
                  "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 2, "relation": "eq" },
                    "max_score": 1.0,
                    "hits": [
                      {
                        "_index": "test-index",
                        "_id": "record-1",
                        "_score": 1.0,
                        "_source": { "title": "Title 1", "description": "Desc 1" },
                        "fields": {},
                        "sort": [],
                        "inner_hits": {},
                        "highlight": {},
                        "_seq_no": 1,
                        "_primary_term": 1,
                        "_version": 1,
                        "_explanation": {},
                        "matched_queries": []
                      },
                      {
                        "_index": "test-index",
                        "_id": "record-2",
                        "_score": 1.0,
                        "_source": { "title": "Title 2", "description": "Desc 2" },
                        "fields": {},
                        "sort": [],
                        "inner_hits": {},
                        "highlight": {},
                        "_seq_no": 2,
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
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val mockSearchResponse = json.decodeFromString<com.jillesvangurp.ktsearch.SearchResponse>(mockResponseJson)

            coEvery { esService.getIndexDocuments("test-collection", any(), any(), any()) } returns mockSearchResponse

            client.get("/ogc/records/collections/test-collection/items?f=geodcat").apply {
                val body = bodyAsText()
                assertEquals(HttpStatusCode.OK, status, "Status should be OK. Body: $body")
                assertTrue(body.contains("<dct:title>Title 1</dct:title>"), "Should contain Title 1")
                assertTrue(body.contains("<dct:title>Title 2</dct:title>"), "Should contain Title 2")
                assertTrue(body.contains("rdf:about=\"/ogc/records/collections/test-collection/items/record-1\""), "Should contain record-1 URI")
                assertTrue(body.contains("rdf:about=\"/ogc/records/collections/test-collection/items/record-2\""), "Should contain record-2 URI")
            }
        }
}
