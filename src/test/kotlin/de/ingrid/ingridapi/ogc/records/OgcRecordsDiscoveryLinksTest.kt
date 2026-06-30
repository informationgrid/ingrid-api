package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that every endpoint advertises all formats actually supported by the server
 * via a `self` link plus `alternate` links (OGC API discovery requirement).
 */
class OgcRecordsDiscoveryLinksTest {
    private fun pairsOfRelType(arr: kotlinx.serialization.json.JsonArray): List<Pair<String, String>> =
        arr.map {
            val o = it.jsonObject
            (o["rel"]?.jsonPrimitive?.content ?: "") to (o["type"]?.jsonPrimitive?.content ?: "")
        }

    @Test
    fun testLandingPageAdvertisesAllCollectionFormats() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            val client = createClient { install(ContentNegotiation) { json() } }
            client.get("/ogc/records/?f=json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                val links = body["links"]!!.jsonArray
                val pairs = pairsOfRelType(links)
                // self link is JSON (current format)
                assertTrue(pairs.contains("self" to "application/json"), "missing self JSON, got $pairs")
                // alternate for HTML must be present
                assertTrue(pairs.contains("alternate" to "text/html"), "missing alternate HTML, got $pairs")
            }
        }

    @Test
    fun testCollectionDetailAdvertisesAllFormats() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            val client = createClient { install(ContentNegotiation) { json() } }
            client.get("/ogc/records/collections/test-id?f=json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                val links = body["links"]!!.jsonArray
                val pairs = pairsOfRelType(links)
                assertTrue(pairs.contains("self" to "application/json"))
                assertTrue(pairs.contains("alternate" to "text/html"))
                // items links for every item format
                assertTrue(pairs.contains("items" to "application/vnd.ingrid.index+json"))
                assertTrue(pairs.contains("items" to "text/html"))
            }
        }

    @Test
    fun testItemsListAdvertisesSelfAndAlternateForCurrentFormat() =
        testApplication {
            application {
                configureSerialization()
                val records = mockk<RecordsService>(relaxed = true)
                coEvery { records.getRecords(any(), any(), any(), any()) } returns null
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { records }
                }
                configureOgcRecordsRouting()
            }
            val client = createClient { install(ContentNegotiation) { json() } }
            client.get("/ogc/records/collections/test-id/items?f=ingrid-index-json").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

    @Test
    fun testSingleItemAdvertisesAllItemFormats() =
        testApplication {
            application {
                configureSerialization()
                val records = mockk<RecordsService>()
                coEvery { records.getRecord("test-id", "rec-1") } returns
                    buildJsonObject { put("title", "demo") }
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { records }
                }
                configureOgcRecordsRouting()
            }
            val client = createClient { install(ContentNegotiation) { json() } }
            client.get("/ogc/records/collections/test-id/items/rec-1?f=ingrid-index-json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                val links = body["links"]?.jsonArray
                assertNotNull(links)
                val pairs = pairsOfRelType(links)
                assertTrue(
                    pairs.contains("self" to "application/vnd.ingrid.index+json"),
                    "missing self ingrid index, got $pairs",
                )
                assertTrue(pairs.contains("alternate" to "text/html"), "missing alternate HTML, got $pairs")
                assertTrue(pairs.any { it.first == "collection" }, "missing collection link, got $pairs")
            }
        }
}
