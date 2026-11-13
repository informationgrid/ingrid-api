package de.ingrid.ingridapi.opensearch

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.opensearch.service.OpenSearchService
import de.ingrid.ingridapi.opensearch.service.UvpSupport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for the isolated UVP support of the OpenSearch interface. This test class is part of
 * the UVP feature and is removed together with [UvpSupport] when the feature is not needed
 * anymore.
 */
class UvpSupportTest {
    @Test
    fun testLatestPhaseMapping() {
        assertEquals("Öffentliche Auslegung", UvpSupport.latestPhase("phase1"))
        assertEquals("Erörterungstermin", UvpSupport.latestPhase("phase1, phase2"))
        assertEquals("Entscheidung über die Zulassung", UvpSupport.latestPhase("phase3"))
        assertEquals("Unterrichtung über den Untersuchungsrahmen", UvpSupport.latestPhase("scopeOfInvestigation"))
        assertEquals(null, UvpSupport.latestPhase("unknownPhase"))
    }

    @Test
    fun testUvpModeChangesChannelAndItems() =
        addWrapper { client, _ ->
            client.get("/opensearch/query?q=boden&uvp=1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                // the uvp namespace is declared
                assertContains(body, """xmlns:uvp="http://www.uvp-verbund.de/opensearch/extension/1.0"""")
                // the OpenSearch response elements are omitted in UVP mode
                assertFalse(body.contains("<opensearch:totalResults>"))
                assertFalse(body.contains("<opensearch:startIndex>"))
                assertFalse(body.contains("<opensearch:itemsPerPage>"))
                assertFalse(body.contains("<opensearch:Query"))
                // the channel contains the pubDate of the most recent hit
                assertContains(body, "<pubDate>2025-08-06T02:00:00+02:00</pubDate>")
                // the item contains the UVP elements instead of the relevance score
                assertContains(body, "<guid>record-1</guid>")
                assertContains(body, "<author>Max Mustermann</author>")
                assertContains(body, "<uvp:latest-phase>Erörterungstermin</uvp:latest-phase>")
                assertFalse(body.contains("<relevance:score>"))
            }
        }

    @Test
    fun testWithoutUvpParameter() =
        addWrapper { client, _ ->
            client.get("/opensearch/query?q=boden").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, "<opensearch:totalResults>1</opensearch:totalResults>")
                assertContains(body, "<relevance:score>1.5</relevance:score>")
                assertFalse(body.contains("xmlns:uvp"))
                assertFalse(body.contains("<guid>"))
                assertFalse(body.contains("<author>"))
                assertFalse(body.contains("<pubDate>"))
            }
        }

    @Test
    fun testUvpChannelLanguageAndCopyright() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "ktor.opensearch.uvp.language" to "de",
                        "ktor.opensearch.uvp.copyright" to "UVP-Verbund",
                    )
            }
            val esMock = mockkClass(ElasticsearchService::class)
            coEvery { esMock.search(any()) } returns searchResultWithUvpFields()
            coEvery { esMock.getActiveCatalogs() } returns emptyList()
            application {
                dependencies {
                    provide<OpenSearchService> { OpenSearchService(esMock) }
                }
                configureOpenSearchRouting()
            }
            val client = createClient { }

            client.get("/opensearch/query?q=boden&uvp=1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, "<language>de</language>")
                assertContains(body, "<copyright>UVP-Verbund</copyright>")
            }
        }

    /** A single hit with UVP fields, modification time as epoch milliseconds. */
    private fun searchResultWithUvpFields(): SearchResult =
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
                                    put("modified", JsonPrimitive(1754438400000))
                                    put("uvp_steps", "phase1, phase2")
                                    put("uvp_address", "Max Mustermann")
                                },
                            )
                        },
                    )
                },
        )

    private fun addWrapper(block: suspend ApplicationTestBuilder.(HttpClient, ElasticsearchService) -> Unit) {
        testApplication {
            val esMock = mockkClass(ElasticsearchService::class)
            coEvery { esMock.search(any()) } returns searchResultWithUvpFields()
            coEvery { esMock.getActiveCatalogs() } returns emptyList()
            application {
                dependencies {
                    provide<OpenSearchService> { OpenSearchService(esMock) }
                }
                configureOpenSearchRouting()
            }
            val client = createClient { }

            block(client, esMock)
        }
    }
}
