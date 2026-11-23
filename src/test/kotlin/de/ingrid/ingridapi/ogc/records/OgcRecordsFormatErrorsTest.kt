package de.ingrid.ingridapi.ogc.records

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.ogc.records.services.RecordsService
import de.ingrid.ingridapi.plugins.configureSwagger
import de.ingrid.ingridapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OgcRecordsFormatErrorsTest {
    @Test
    fun testInvalidFormatParameterOnCollections() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            client.get("/ogc/records/collections?f=pdf").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                val body = bodyAsText()
                assertTrue(body.contains("InvalidParameterValue"), "body=$body")
                assertTrue(body.contains("pdf"))
                assertTrue(body.contains("\"allowedValues\""))
                assertTrue(body.contains("\"json\""))
                assertTrue(body.contains("\"html\""))
            }
        }

    @Test
    fun testInvalidFormatParameterOnItems() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            client.get("/ogc/records/collections/test-id/items?f=pdf").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                val body = bodyAsText()
                assertTrue(body.contains("InvalidParameterValue"), "body=$body")
                assertTrue(body.contains("\"ingrid-index-json\""))
            }
        }

    @Test
    fun testOpenApiItemFormatParametersUseParamValues() =
        testApplication {
            application {
                configureSwagger()
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            val spec = Json.parseToJsonElement(client.get("/ogc/records/api").bodyAsText()).jsonObject
            val paths = spec.getValue("paths").jsonObject
            val expectedFormats = listOf("html", "iso", "geojson", "ingrid-index-json", "geodcat")
            val itemPaths =
                listOf(
                    "/ogc/records/collections/{catalogId}/items",
                    "/ogc/records/collections/{catalogId}/items/{recordId}",
                )

            itemPaths.forEach { path ->
                val parameters =
                    paths
                        .getValue(path)
                        .jsonObject
                        .getValue("get")
                        .jsonObject
                        .getValue("parameters")
                        .jsonArray
                val formatParameter =
                    parameters.first {
                        it.jsonObject.getValue("name").jsonPrimitive.content == "format"
                    }.jsonObject
                val formats =
                    formatParameter
                        .getValue("schema")
                        .jsonObject
                        .getValue("enum")
                        .jsonArray
                        .map { it.jsonPrimitive.content }
                assertEquals(expectedFormats, formats, "path=$path")
            }
        }

    @Test
    fun testNotAcceptableOnCollections() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            client
                .get("/ogc/records/collections") {
                    headers[HttpHeaders.Accept] = "application/xml"
                }.apply {
                    val body = bodyAsText()
                    assertEquals(HttpStatusCode.NotAcceptable, status, "body=$body")
                    assertTrue(body.contains("NotAcceptable"), "body=$body")
                    assertTrue(body.contains("application/json"), "body=$body")
                    assertTrue(body.contains("text/html"), "body=$body")
                }
        }

    @Test
    fun testNotAcceptableOnItems() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            client
                .get("/ogc/records/collections/test-id/items/rec-1") {
                    headers.remove(HttpHeaders.Accept)
                    header(HttpHeaders.Accept, "application/pdf")
                }.apply {
                    val body = bodyAsText()
                    assertEquals(HttpStatusCode.NotAcceptable, status, "body=$body")
                    assertTrue(body.contains("NotAcceptable"))
                    assertTrue(body.contains("application/vnd.ingrid.index+json"))
                    assertTrue(body.contains("text/html"))
                }
        }

    @Test
    fun testBrowserWildcardFallsBackToHtml() =
        testApplication {
            application {
                configureSerialization()
                dependencies {
                    provide<ElasticsearchService> { mockk(relaxed = true) }
                    provide<RecordsService> { mockk(relaxed = true) }
                }
                configureOgcRecordsRouting()
            }
            // Browser-Style: Accept enthält */*
            client
                .get("/ogc/records/collections/test-id") {
                    headers.remove(HttpHeaders.Accept)
                    header(HttpHeaders.Accept, "*/*")
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                }
        }
}
