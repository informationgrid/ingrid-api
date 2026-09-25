package de.ingrid.ingridapi.admin

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
    fun testAdminSearchFieldSearch() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val hits =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_id", JsonPrimitive("456"))
                            put("_index", JsonPrimitive("test-index"))
                            put(
                                "_source",
                                buildJsonObject {
                                    put("title", JsonPrimitive("Field Search Test"))
                                    put("author", JsonPrimitive("John Doe"))
                                },
                            )
                        },
                    )
                }
            val querySlot = io.mockk.slot<String>()
            coEvery { esMock.search(capture(querySlot)) } returns SearchResult(1, hits)

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search?q=author:John").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Field Search Test"))

                // Verify that query_string query was created for the field search
                val capturedQuery = Json.parseToJsonElement(querySlot.captured).jsonObject
                val queryObj = capturedQuery["query"]?.jsonObject
                assertTrue(queryObj?.containsKey("query_string") ?: false, "Should use query_string for field search")
                val queryStringObj = queryObj["query_string"]?.jsonObject
                assertEquals("author:John", queryStringObj?.get("query")?.jsonPrimitive?.content)
                assertEquals("*", queryStringObj?.get("default_field")?.jsonPrimitive?.content)
                assertEquals("AND", queryStringObj?.get("default_operator")?.jsonPrimitive?.content)
            }
        }

    @Test
    fun testAdminSearchMultipleFields() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            val hits =
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("_id", JsonPrimitive("999"))
                            put("_index", JsonPrimitive("test-index"))
                            put(
                                "_source",
                                buildJsonObject {
                                    put("title", JsonPrimitive("Multiple Field Test"))
                                    put("author", JsonPrimitive("Jane Doe"))
                                    put("category", JsonPrimitive("Tech"))
                                },
                            )
                        },
                    )
                }
            val querySlot = io.mockk.slot<String>()
            coEvery { esMock.search(capture(querySlot)) } returns SearchResult(1, hits)

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin/search?q=author:Jane category:Tech").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("Multiple Field Test"))

                // Verify that query_string query was created for multiple field searches
                val capturedQuery = Json.parseToJsonElement(querySlot.captured).jsonObject
                val queryObj = capturedQuery["query"]?.jsonObject
                assertTrue(
                    queryObj?.containsKey("query_string") ?: false,
                    "Should use query_string for multiple field searches",
                )
                val queryStringObj = queryObj["query_string"]?.jsonObject
                assertEquals("author:Jane category:Tech", queryStringObj?.get("query")?.jsonPrimitive?.content)
                assertEquals("*", queryStringObj?.get("default_field")?.jsonPrimitive?.content)
                assertEquals("AND", queryStringObj?.get("default_operator")?.jsonPrimitive?.content)
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
            io.mockk.every { esMock.metaIndexName } returns "ingrid_meta"
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
            io.mockk.every { esMock.metaIndexName } returns "ingrid_meta"
            coEvery { esMock.deleteDocument("ingrid_meta", "meta-1") } returns Unit

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
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

    @Test
    fun testAdminIndicesPageWithPrefix() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            io.mockk.every { esMock.indexPrefix } returns "pre_"
            io.mockk.every { esMock.metaIndexName } returns "pre_ingrid_meta"
            coEvery { esMock.listIndicesWithAliases() } returns
                mapOf(
                    "pre_index1" to emptySet(),
                    "other_index" to emptySet(),
                )
            coEvery { esMock.listIndicesConfig() } returns
                buildJsonObject {
                    put(
                        "pre_index1",
                        buildJsonObject {
                            put(
                                "mappings",
                                buildJsonObject {
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("title", buildJsonObject { put("type", JsonPrimitive("text")) })
                                        },
                                    )
                                },
                            )
                            put(
                                "settings",
                                buildJsonObject {
                                    put("index", buildJsonObject { put("number_of_shards", JsonPrimitive("1")) })
                                },
                            )
                        },
                    )
                }
            coEvery { esMock.getMetaEntries() } returns
                listOf(
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc1", "id1", "pre_index1", true, "Prefixed Source"),
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc2", "id2", "other_index", true, "Other Source"),
                )
            coEvery { esMock.countDocuments(any()) } returns 10L

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("pre_"))
                assertTrue(body.contains("Verwaltete Indizes mit Präfix 'pre_'"))
                assertTrue(body.contains("Prefixed Source"))
                assertTrue(body.contains("Mapping &amp; Settings"))
                assertTrue(body.contains("number_of_shards"))
                assertTrue(body.contains("Weitere verwaltete Indizes"))
                assertTrue(body.contains("Other Source"))
            }
        }

    @Test
    fun testAdminIndicesPageWithMultipleDataSourcesPerIndex() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            io.mockk.every { esMock.indexPrefix } returns ""
            io.mockk.every { esMock.metaIndexName } returns "ingrid_meta"
            coEvery { esMock.listIndicesWithAliases() } returns
                mapOf(
                    "shared_index" to emptySet(),
                    "other_index" to emptySet(),
                )
            coEvery { esMock.listIndicesConfig() } returns buildJsonObject {}
            coEvery { esMock.getMetaEntries() } returns
                listOf(
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc1", "id1", "shared_index", true, "Data Source 1"),
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc2", "id2", "shared_index", true, "Data Source 2"),
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc3", "id3", "other_index", true, "Other Source"),
                )
            coEvery { esMock.countDocuments(any()) } returns 10L

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            client.get("/admin").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                // Both datasources should be grouped under the same index
                assertTrue(
                    body.contains("Data Source 1, Data Source 2"),
                    "Should show both datasource names for shared_index",
                )
                assertTrue(body.contains("Other Source"), "Should show Other Source for other_index")
                // The index name should be visible
                assertTrue(body.contains("shared_index"), "Should show the index name")
                assertTrue(body.contains("other_index"), "Should show the other index name")
                // Should have toggle for the grouped index
                assertTrue(body.contains("meta/index/shared_index/active"), "Should have group toggle endpoint")
                // Should have search links for each datasource (without index filtering)
                assertTrue(body.contains("Search in Data Source 1"), "Should have search link for Data Source 1")
                assertTrue(body.contains("Search in Data Source 2"), "Should have search link for Data Source 2")
                assertTrue(body.contains("collection.name:Data+Source+1"), "Search link should use field search syntax")
            }
        }

    @Test
    fun testAdminToggleGroupedIndex() =
        testApplication {
            val esMock = mockk<ElasticsearchService>()
            io.mockk.every { esMock.metaIndexName } returns "ingrid_meta"
            val metaEntries =
                listOf(
                    de.ingrid.ingridapi.core.services.IngridMetaEntry(
                        "doc1",
                        "id1",
                        "shared_index",
                        false,
                        "Data Source 1",
                    ),
                    de.ingrid.ingridapi.core.services
                        .IngridMetaEntry("doc2", "id2", "shared_index", false, "Data Source 2"),
                )
            coEvery { esMock.getMetaEntries() } returns metaEntries
            coEvery { esMock.setMetaActive("doc1", true) } returns Unit
            coEvery { esMock.setMetaActive("doc2", true) } returns Unit

            application {
                install(Authentication) {
                    val provider =
                        object : AuthenticationProvider(object : Config("admin-session") {}) {
                            override suspend fun onAuthenticate(context: AuthenticationContext) {
                                context.principal(object : Any() {})
                            }
                        }
                    register(provider)
                }
                dependencies.provide<ElasticsearchService> { esMock }
                configureAdminRouting()
            }

            // Toggle the grouped index to active
            val response =
                client.post("/admin/meta/index/shared_index/active") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("active=true")
                }
            assertEquals(HttpStatusCode.Found, response.status)
            val location = response.headers[HttpHeaders.Location]
            assertTrue(
                location?.contains("Data+Source+1") ?: false,
                "Should redirect with success message containing both datasource names. Location: $location",
            )
        }
}
