package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.base
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.portal
import de.ingrid.ingridapi.portal.model.Catalog
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.post
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.koin.test.KoinTest
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesKtTest : KoinTest {
//    private val mockElasticsearchService = mockk<ElasticsearchService>()

    @Test
    fun testPostPortalSearch() =
        testApplication {
            /*application {
                portal()
            }*/
            client.post("/portal/search").apply {
                TODO("Please write your test here")
            }
        }

/*    @get:Rule
    val koinTestRule =
        KoinTestRule.create {
            // Your KoinApplication instance here
            modules(
                module {
                    single { AppConfig() }
                    single { ElasticsearchService(get()) }
                },
            )
        }*/

    @get:Rule
    val mockProvider =
        MockProviderRule.create { clazz ->
            // Your way to build a Mock here
            mockkClass(clazz, relaxed = true)
        }

    @Test
    fun testGetPortalCatalogs() =
        withTestApplication {
            application.base()
            application.portal()

            val mock = declareMock<ElasticsearchService>()
            coEvery { mock.search(any()) } returns
                SearchResult(
                    1,
                    emptyList(),
                    Json.decodeFromString<JsonObject>(
                        """
                        { "catalogs": {"buckets":  [{"key":  "aaa", "info":  {"hits":  {"hits":  [{"_source": {"dataSourceName":  "ds name", "partner":  ["he"], "datatype":  ["www"]}}]}}}]} }
                        """.trimIndent(),
                    ),
                )

            with(handleRequest(HttpMethod.Get, "portal/catalogs")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val catalogs = Json.decodeFromString<List<Catalog>>(response.content!!)
                assertEquals(1, catalogs.size)
                assertEquals(Catalog("aaa", "ds name", listOf("he"), false, false), catalogs[0])
            }
        }

    @Test
    fun testGetPortalCatalogsIdHierarchy() =
        testApplication {
            /*application {
                portal()
            }*/
            client.get("/portal/catalogs/{id}/hierarchy").apply {
                TODO("Please write your test here")
            }
        }
}
