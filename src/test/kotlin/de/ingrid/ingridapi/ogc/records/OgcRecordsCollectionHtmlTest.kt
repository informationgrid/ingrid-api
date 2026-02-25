package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.testing.testApplication
import io.mockk.mockkClass
import org.junit.Rule
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsCollectionHtmlTest : KoinTest {
    @get:Rule
    val koinTestRule =
        KoinTestRule.create {
            modules(
                module {
                    single { mockkClass(RecordsService::class) }
                },
            )
        }

    @get:Rule
    val mockProvider = MockProviderRule.create { mockkClass(it) }

    @Test
    fun testGetCollectionHtml() =
        testApplication {
            application {
                configureSerialization()
                configureOgcRecordsRouting()
            }

            // Test HTML representation
            client.get("/ogc/records/collections/test-id?f=html").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText()
                assertTrue(body.contains("<h1>Test-id</h1>"), "Should contain collection title")
                assertTrue(body.contains("<code>test-id</code>"), "Should contain collection id")
                assertTrue(body.contains("Description for collection &#39;test-id&#39;"), "Should contain description")
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id/items\""),
                    "Should contain link to items",
                )
                assertTrue(
                    body.contains("href=\"/ogc/records/collections/test-id?f=html\""),
                    "Should contain link to self (html)",
                )
            }

            // Test JSON representation (default)
            client.get("/ogc/records/collections/test-id").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), contentType())
                val body = bodyAsText().replace("\\s".toRegex(), "")
                assertTrue(body.contains("\"id\":\"test-id\""), "Should contain id in JSON")
            }
        }
}
