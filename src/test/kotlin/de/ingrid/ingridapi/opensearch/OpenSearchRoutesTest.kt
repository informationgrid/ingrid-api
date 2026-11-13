package de.ingrid.ingridapi.opensearch

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.opensearch.service.OpenSearchService
import de.ingrid.ingridapi.plugins.configureSwagger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenSearchRoutesTest {
    @Test
    fun testSwaggerUi() =
        addWrapper { client, _ ->
            client.get("/opensearch/swagger").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>Swagger UI</title>")
            }
        }

    @Test
    fun testOpenApiSpec() =
        addWrapper { client, _ ->
            client.get("/opensearch/api").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "OpenSearch API")
            }
        }

    @Test
    fun testLandingPage() =
        addWrapper { client, _ ->
            client.get("/opensearch").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "OpenSearch API")
            }
        }

    @Test
    fun testDescriptor() =
        addWrapper { client, _ ->
            client.get("/opensearch/descriptor").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<OpenSearchDescription")
                assertContains(bodyAsText(), "<ShortName>InGrid OpenSearch</ShortName>")
                assertContains(bodyAsText(), "/opensearch/query?q={searchTerms}")
                assertContains(bodyAsText(), "application/rss+xml")
            }
        }

    @Test
    fun testQueryRendersRss() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
                SearchResult(
                    totalHits = 1,
                    hits =
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("_index", "test-index")
                                    put("_id", "record-1")
                                    put("_score", 1.5)
                                    put(
                                        "_source",
                                        buildJsonObject {
                                            put("title", "Record 1")
                                            put("description", "Description 1")
                                        },
                                    )
                                },
                            )
                        },
                )
            coEvery { esMock.getActiveCatalogs() } returns
                listOf(
                    JsonObject(
                        mapOf(
                            "linkedIndex" to JsonPrimitive("test-index"),
                            "plugdescription" to
                                JsonObject(
                                    mapOf("dataSourceName" to JsonPrimitive("test-catalog")),
                                ),
                        ),
                    ),
                )

            client.get("/opensearch/query?q=boden&p=1&h=10").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, """<rss xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/"""")
                assertContains(body, "<title>ingrid OpenSearch: boden</title>")
                assertContains(body, "<opensearch:totalResults>1</opensearch:totalResults>")
                assertContains(body, "<opensearch:startIndex>1</opensearch:startIndex>")
                assertContains(body, "<opensearch:itemsPerPage>10</opensearch:itemsPerPage>")
                assertContains(body, """<opensearch:Query role="request" searchTerms="boden"/>""")
                assertContains(body, "<title>Record 1</title>")
                assertContains(body, "<description>Description 1</description>")
                assertContains(body, "<relevance:score>1.5</relevance:score>")
                assertContains(body, "<ingrid:docuuid>record-1</ingrid:docuuid>")
                assertContains(body, "<ingrid:source>test-catalog</ingrid:source>")
                assertContains(
                    body,
                    "/ogc/records/collections/test-catalog/items/record-1?f=html</link>",
                )
            }
        }

    @Test
    fun testQueryTranslatesIngridQuery() =
        addWrapper { client, esMock ->
            val captured = slot<String>()
            coEvery { esMock.search(capture(captured)) } returns SearchResult(0, buildJsonArray { })
            coEvery { esMock.getActiveCatalogs() } returns emptyList()

            client.get("/opensearch/query?q=wasser%20datatype:default%20ranking:score").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            val esQuery = Json.parseToJsonElement(captured.captured).jsonObject
            // the search term is translated into a multi_match query...
            assertContains(esQuery.toString(), """"query":"wasser"""")
            // ...while the meta terms are dropped
            assertFalse(esQuery.toString().contains("datatype"))
            assertFalse(esQuery.toString().contains("ranking"))
        }

    @Test
    fun testQueryClampsHitsPerPage() =
        addWrapper { client, esMock ->
            val captured = io.mockk.slot<String>()
            coEvery { esMock.search(capture(captured)) } returns SearchResult(0, buildJsonArray { })
            coEvery { esMock.getActiveCatalogs() } returns emptyList()

            client.get("/opensearch/query?q=boden&h=500&p=3").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            val esQuery = Json.parseToJsonElement(captured.captured).jsonObject
            assertEquals(100, esQuery["size"]!!.jsonPrimitive.content.toInt())
            assertEquals(200, esQuery["from"]!!.jsonPrimitive.content.toInt())
        }

    @Test
    fun testQueryRejectsUnsupportedFormat() =
        addWrapper { client, _ ->
            client.get("/opensearch/query?q=boden&format=rdf").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }
        }

    private fun addWrapper(block: suspend ApplicationTestBuilder.(HttpClient, ElasticsearchService) -> Unit) {
        testApplication {
            val esMock = mockkClass(ElasticsearchService::class)
            val client = createClient { }
            application {
                dependencies {
                    provide<OpenSearchService> { OpenSearchService(esMock) }
                }
                configureSwagger()
                configureOpenSearchRouting()
            }

            block(client, esMock)
        }
    }
}
