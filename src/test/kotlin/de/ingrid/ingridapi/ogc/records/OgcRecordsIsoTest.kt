package de.ingrid.ingridapi.ogc.records

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsIsoTest {
    @Test
    @Ignore
    fun testGetRecordIso() =
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

            coEvery { esService.getIndexDocument("test-collection", "record-1") } returns JsonObject(emptyMap())
            coEvery { esService.getIndexDocument("test-collection", "non-existent") } returns null

            // Test successful getRecord
            client.get("/ogc/records/collections/test-collection/items/record-1?format=index").apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            // Test 404 for non-existent record
            client.get("/ogc/records/collections/test-collection/items/non-existent?format=index").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }

            // Test ISO format with transformation
            val idfXml =
                """
                <idf:idfMdMetadata xmlns:idf="http://www.portalu.de/IDF/1.0" uuid="test-uuid" id="test-id">
                    <idf:idfResponsibleParty>
                        <idf:individualName>John Doe</idf:individualName>
                    </idf:idfResponsibleParty>
                </idf:idfMdMetadata>
                """.trimIndent()

            coEvery {
                esService.getIndexDocument(
                    "test-collection",
                    "record-iso",
                )
            } returns JsonObject(mapOf("idf" to JsonPrimitive(idfXml)))

            client.get("/ogc/records/collections/test-collection/items/record-iso?format=iso").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertTrue(body.contains("<gmd:MD_Metadata"))
                assertTrue(body.contains("uuid=\"test-uuid\""))
            }
        }
}
