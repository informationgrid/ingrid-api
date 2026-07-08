package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.plugins.configureSerialization
import de.ingrid.ingridapi.portal.model.Catalog
import de.ingrid.ingridapi.portal.model.ResponseHierarchy
import de.ingrid.ingridapi.portal.services.CatalogService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesKtTest {
    @Ignore
    @Test
    fun testPostPortalSearchEmpty() =
        appWrapper { client, _ ->
            client.post("/portal/search").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

    @Test
    fun testGetPortalCatalogs() =
        appWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
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

            client.get("portal/catalogs").apply {
                assertEquals(HttpStatusCode.OK, status)
                val catalogs = body<List<Catalog>>()
                assertEquals(1, catalogs.size)
                assertEquals(Catalog("aaa", "ds name", listOf("he"), false, false), catalogs[0])
            }
        }

    @Test
    fun testGetPortalCatalogsIdHierarchyEmpty() =
        appWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns createSearchResult("[]")
            client.get("/portal/catalogs/test-catalog/hierarchy").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(emptyList<List<ResponseHierarchy>>(), body())
            }
        }

    @Test
    fun testGetPortalCatalogsIdHierarchyRoot() =
        appWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
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
            client.get("/portal/catalogs/test-catalog/hierarchy").apply {
                assertEquals(HttpStatusCode.OK, status)
                val result = body<List<ResponseHierarchy>>()
                assertEquals(1, result.size)
                assertEquals(listOf(ResponseHierarchy("1", "doc1", "0", false, false)), result)
            }
        }

    private fun appWrapper(block: suspend ApplicationTestBuilder.(HttpClient, ElasticsearchService) -> Unit) =
        testApplication {
            val esMock = mockkClass(ElasticsearchService::class)
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            application {
                dependencies.provide<ElasticsearchService> { esMock }
                dependencies.provide(::CatalogService)
                configurePortalRouting()
                configureSerialization()
            }

            block(client, esMock)
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
