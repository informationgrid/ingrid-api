package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ensures the "portal" section provides its own Swagger UI and OpenAPI specification
 * (in addition to the ones of the "ogc/records" section).
 */
class PortalSwaggerTest {
    @Test
    fun testSwaggerUi() =
        addWrapper { client ->
            client.get("/portal").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>Swagger UI</title>")
            }
        }

    @Test
    fun testOpenApiSpec() =
        addWrapper { client ->
            client.get("/portal/api.json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                assertTrue(body["openapi"]!!.jsonPrimitive.content.startsWith("3."))
                assertEquals("Portal API", body["info"]!!.jsonObject["title"]?.jsonPrimitive?.content)
                val paths = body["paths"]!!.jsonObject.keys
                assertTrue("/portal/search" in paths, "spec must contain /portal/search")
                assertTrue("/portal/catalogs" in paths, "spec must contain /portal/catalogs")
                assertTrue(
                    "/portal/catalogs/{id}/hierarchy" in paths,
                    "spec must contain /portal/catalogs/{id}/hierarchy",
                )
            }
        }

    @Test
    fun testOpenApiSpecContainsOnlyPortalRoutes() =
        addWrapper { client ->
            client.get("/portal/api.json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = body<JsonObject>()
                body["paths"]!!
                    .jsonObject
                    .keys
                    .forEach { path ->
                        assertTrue(path.startsWith("/portal"), "spec must only contain portal routes, got '$path'")
                    }
            }
        }

    private fun addWrapper(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application {
                configureSerialization()
                configurePortalRouting()
            }
            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            block(client)
        }
}
