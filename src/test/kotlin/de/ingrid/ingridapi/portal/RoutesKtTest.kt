package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.plugins.appModule
import de.ingrid.ingridapi.plugins.configureSerialization
import de.ingrid.ingridapi.portal.model.Catalog
import de.ingrid.ingridapi.portal.model.ResponseHierarchy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesKtTest : KoinTest {
    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(appModule) }

    @get:Rule
    val mockProvider = MockProviderRule.create { mockkClass(it) }

    @Ignore
    @Test
    fun testPostPortalSearchEmpty() =
        appWrapper { client ->
            client.post("/portal/search").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

    @Test
    fun testGetPortalCatalogs() =
        appWrapper { client ->
            declareMock<ElasticsearchService> {
                coEvery { search(any()) } returns
                    createSearchResult(
                        "[]",
                        """{
  "catalogs": {
    "buckets": [
      {
        "key": "aaa",
        "info": {
          "hits": {
            "hits": [
              {
                "_source": {
                  "dataSourceName": "ds name",
                  "partner": [
                    "he"
                  ],
                  "datatype": [
                    "www"
                  ]
                } } ] } } } ] } }""",
                    )
            }

            client.get("portal/catalogs").apply {
                assertEquals(HttpStatusCode.OK, status)
                val catalogs = body<List<Catalog>>()
                assertEquals(1, catalogs.size)
                assertEquals(Catalog("aaa", "ds name", listOf("he"), false, false), catalogs[0])
            }
        }

    @Test
    fun testGetPortalCatalogsIdHierarchyEmpty() =
        appWrapper { client ->
            declareMock<ElasticsearchService> {
                coEvery { search(any()) } returns createSearchResult("[]")
            }
            client.get("/portal/catalogs/test-catalog/hierarchy").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(emptyList<List<ResponseHierarchy>>(), body())
            }
        }

    @Test
    fun testGetPortalCatalogsIdHierarchyRoot() =
        appWrapper { client ->
            declareMock<ElasticsearchService> {
                coEvery { search(any()) } returns
                    createSearchResult(
                        """
                            [
                              {
                                "_index": "xxx",
                                "_type": "folder",
                                "_id": "1",
                                "_source": {
                                  "title": "doc1",
                                  "t01_object.obj_class": "0",
                                  "isfolder": "false"
                                }
                              }
                            ]
                            """,
                    )
            }
            client.get("/portal/catalogs/test-catalog/hierarchy").apply {
                assertEquals(HttpStatusCode.OK, status)
                val result = body<List<ResponseHierarchy>>()
                assertEquals(1, result.size)
                assertEquals(listOf(ResponseHierarchy("1", "doc1", "0", false, false)), result)
            }
        }

    private fun appWrapper(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            application {
                configurePortalRouting()
                configureSerialization()
            }

            block(client)
        }

    private fun createSearchResult(
        hits: String,
        aggs: String? = null,
    ): SearchResult =
        SearchResult(
            1,
            Json.decodeFromString<JsonArray>(
                hits.trimIndent(),
            ),
            aggs?.let {
                Json.decodeFromString<JsonObject>(
                    aggs.trimIndent(),
                )
            },
        )
}
