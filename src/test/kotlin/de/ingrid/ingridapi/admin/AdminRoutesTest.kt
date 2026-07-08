package de.ingrid.ingridapi.admin

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminRoutesTest {
    @Test
    fun testAdminSearchPage() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertTrue(bodyAsText().contains("Suche"))
            }
        }

    @Test
    fun testAdminSearchResults() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val hits =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_id", JsonPrimitive("123"))
                            put("_index", JsonPrimitive("test-index"))
                            put(
                                "_source",
                                buildJsonObject {
                                    put("title", JsonPrimitive("Test Document"))
                                },
                            )
                        },
                    )
                }
            coEvery { esMock.search(any()) } returns SearchResult(1, hits)

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search?q=test").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Test Document"))
                assertTrue(body.contains("test-index"))
                assertTrue(body.contains("123"))
            }
        }

    @Test
    fun testAdminViewDocument() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val doc =
                buildJsonObject {
                    put("title", JsonPrimitive("Test Document"))
                    put("content", JsonPrimitive("Some content"))
                }
            coEvery { esMock.getDocument("test-index", "123") } returns doc

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search/view?index=test-index&id=123").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Test Document"))
                assertTrue(body.contains("Some content"))
            }
        }

    @Test
    fun testAdminMetaPage() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            coEvery { esMock.getMetaEntries() } returns
                listOf(
                    de.ingrid.ingridapi.core.services.IngridMetaEntry(
                        docId = "meta-1",
                        indexId = "id-1",
                        linkedIndex = "index-1",
                        active = true,
                        dataSourceName = "Source 1",
                    ),
                )

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/meta").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Meta-Verwaltung"))
                assertTrue(body.contains("Source 1"))
                assertTrue(body.contains("meta-1"))
            }
        }

    @Test
    fun testAdminDeleteMetaEntry() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            coEvery { esMock.deleteDocument("ingrid_meta", "meta-1") } returns Unit

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.post("/admin/meta/meta-1/delete").apply {
                assertEquals(HttpStatusCode.Found, status)
                assertTrue(headers[HttpHeaders.Location]!!.contains("/admin/meta"))
            }
        }

    @Test
    fun testAdminEmptySearch() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val hits =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_id", JsonPrimitive("empty-1"))
                            put("_index", JsonPrimitive("test-index"))
                            put(
                                "_source",
                                buildJsonObject {
                                    put("title", JsonPrimitive("Default result"))
                                },
                            )
                        },
                    )
                }
            coEvery { esMock.search(any()) } returns SearchResult(1, hits)

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            // When q is empty, it should now return results (after my fix)
            client.get("/admin/search").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Default result"), "Should show results even for empty query")
            }
        }

    @Test
    fun testAdminSearchPagination() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val querySlot = io.mockk.slot<String>()
            coEvery { esMock.search(capture(querySlot)) } returns SearchResult(45, buildJsonArray {})

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : AuthenticationProvider.Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Principal {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search?q=test&page=2").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("pagination"), "Should contain pagination nav")
                assertTrue(body.contains("page=1"), "Should have link to page 1")
                assertTrue(body.contains("page=3"), "Should have link to page 3")

                val capturedQuery = Json.parseToJsonElement(querySlot.captured).jsonObject
                assertEquals(10, capturedQuery["from"]?.jsonPrimitive?.int, "from should be 10 for page 2")
                assertEquals(10, capturedQuery["size"]?.jsonPrimitive?.int, "size should be 10")
            }
        }
}
