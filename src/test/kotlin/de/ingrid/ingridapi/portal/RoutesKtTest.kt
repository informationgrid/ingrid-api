package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.plugins.appModule
import de.ingrid.ingridapi.plugins.configureSerialization
import de.ingrid.ingridapi.portal.model.Catalog
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesKtTest : KoinTest {
    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(appModule) }

    @get:Rule
    val mockProvider = MockProviderRule.create { mockkClass(it) }

    @Test
    fun testGetPortalCatalogs() =
        appWrapper {
            declareMock<ElasticsearchService> {
                coEvery { search(any()) } returns
                    SearchResult(
                        1,
                        emptyList(),
                        Json.decodeFromString<JsonObject>(
                            """
                            { "catalogs": {"buckets":  [{"key":  "aaa", "info":  {"hits":  {"hits":  [{"_source": {"dataSourceName":  "ds name", "partner":  ["he"], "datatype":  ["www"]}}]}}}]} }
                            """.trimIndent(),
                        ),
                    )
            }

            client.get("portal/catalogs").apply {
                assertEquals(HttpStatusCode.OK, status)
                val catalogs = Json.decodeFromString<List<Catalog>>(bodyAsText())
                assertEquals(1, catalogs.size)
                assertEquals(Catalog("aaa", "ds name", listOf("he"), false, false), catalogs[0])
            }
        }

    private fun appWrapper(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                configurePortalRouting()
                configureSerialization()
            }

            block(this)
        }
}
