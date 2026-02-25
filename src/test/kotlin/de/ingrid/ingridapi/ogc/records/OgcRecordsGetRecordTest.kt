package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.Test
import kotlin.test.assertEquals

class OgcRecordsGetRecordTest : KoinTest {
    @get:Rule
    val koinTestRule =
        KoinTestRule.create {
            modules(
                module {
                    single { mockkClass(ElasticsearchService::class) }
                    single { RecordsService(get()) }
                },
            )
        }

    @get:Rule
    val mockProvider = MockProviderRule.create { mockkClass(it) }

    @Test
    fun testGetRecord() =
        testApplication {
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            application {
                configureSerialization()
                configureOgcRecordsRouting()
            }

            declareMock<ElasticsearchService> {
                coEvery { getIndexDocument("test-collection", "record-1") } returns JsonObject(emptyMap())
                coEvery { getIndexDocument("test-collection", "non-existent") } returns null
            }

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

            val esService = get<ElasticsearchService>()
            coEvery {
                esService.getIndexDocument(
                    "test-collection",
                    "record-iso",
                )
            } returns JsonObject(mapOf("idf" to JsonPrimitive(idfXml)))

            client.get("/ogc/records/collections/test-collection/items/record-iso?format=iso").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assert(body.contains("<gmd:MD_Metadata"))
                assert(body.contains("uuid=\"test-uuid\""))
            }
        }
}
