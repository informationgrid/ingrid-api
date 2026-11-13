package de.ingrid.ingridapi.atom

import de.ingrid.ingridapi.atom.service.AtomSearchService
import de.ingrid.ingridapi.atom.service.DatasetFeedProducer
import de.ingrid.ingridapi.atom.service.OpensearchDescriptionProducer
import de.ingrid.ingridapi.atom.service.ServiceFeedProducer
import de.ingrid.ingridapi.config.AppConfig
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.atom.configureAtomRouting
import de.ingrid.ingridapi.plugins.configureSwagger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkClass
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AtomRoutesTest {

    private val config = AppConfig()

    @Test
    fun testSwaggerUi() =
        addWrapper { client, _ ->
            client.get("/atom/swagger").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>Swagger UI</title>")
            }
        }

    @Test
    fun testOpenApiSpec() =
        addWrapper { client, _ ->
            client.get("/atom/api").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "ATOM Download Service")
            }
        }

    @Test
    fun testLandingPageRedirectsToClient() =
        addWrapper(followRedirects = false) { client, _ ->
            client.get("/atom").apply {
                // like the welcome file handling of the Jetty ResourceHandler: redirect to
                // the trailing slash so that the relative URLs of the client resolve
                assertEquals(HttpStatusCode.Found, status)
                assertContains(headers["Location"].orEmpty(), "/atom/")
            }
        }

    @Test
    fun testClientWebsiteIsServed() =
        addWrapper { client, _ ->
            // the welcome file at '/atom/'
            client.get("/atom/").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertContains(bodyAsText(), "<title>ATOM-Feed Client</title>")
            }
            // static resources of the client
            client.get("/atom/favicon.ico").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            client.get("/atom/assets/logo/ingrid.png").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            // unknown resources are not found
            client.get("/atom/does-not-exist.js").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun testFeedEndpointTakesPrecedenceOverStaticContent() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns SearchResult(0, buildJsonArray { })
            // there is no static file 'service-list', so the atom feed endpoint answers
            client.get("/atom/service-list").apply {
                assertEquals(HttpStatusCode.NotFound, status)
                assertContains(bodyAsText(), "No service feeds found")
            }
        }

    @Test
    fun testServiceFeedList() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
                SearchResult(
                    1,
                    buildJsonArray {
                        add(AtomTestHelpers.esHit("service-uuid-1", AtomTestHelpers.SERVICE_IDF))
                    },
                )

            client.get("/atom/service-list").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, """<feed xmlns="http://www.w3.org/2005/Atom"""")
                assertContains(body, "<title>ATOM download service feeds</title>")
                assertContains(body, "<title>Test Download Service</title>")
                assertContains(body, """rel="alternate"""")
                assertContains(body, "/atom/service/service-uuid-1")
                assertContains(body, "<rights>otherRestrictions: Test restriction</rights>")
                assertContains(body, "<updated>2024-01-31T12:00:00</updated>")
                assertContains(body, "<name>Test Organisation</name>")
            }
        }

    @Test
    fun testServiceFeedListEmpty() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns SearchResult(0, buildJsonArray { })
            client.get("/atom/service-list").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun testServiceFeedListTranslatesQuery() =
        addWrapper { client, esMock ->
            val captured = slot<String>()
            coEvery { esMock.search(capture(captured)) } returns SearchResult(0, buildJsonArray { })

            client.get("/atom/service-list?q=wasser%20datatype:default").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
            val esQuery = Json.parseToJsonElement(captured.captured).jsonObject
            // the has_atom_download restriction is always applied...
            assertContains(esQuery.toString(), """"t011_obj_serv.has_atom_download"""")
            // ...and the search term is translated, while the iBus meta term is dropped
            assertContains(esQuery.toString(), """"query":"wasser"""")
            assertFalse(esQuery.toString().contains("datatype"))
        }

    @Test
    fun testServiceFeed() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returnsMany
                listOf(
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("service-uuid-1", AtomTestHelpers.SERVICE_IDF)) }),
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("dataset-uuid-1", AtomTestHelpers.DATASET_IDF)) }),
                )

            client.get("/atom/service/service-uuid-1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, "<title>Test Download Service</title>")
                assertContains(body, "<subtitle>Test service abstract</subtitle>")
                // self link
                assertContains(body, """" rel="self"""")
                // opensearch definition link
                assertContains(body, "application/opensearchdescription+xml")
                // the coupled dataset is listed as entry
                assertContains(body, "<title>Test Dataset</title>")
                assertContains(body, "<summary>Test dataset abstract</summary>")
                assertContains(body, "/atom/dataset/service-uuid-1?datasetUuid=dataset-uuid-1")
                // georss polygon from the bounding box
                assertContains(body, "<georss:polygon>49.5 5.5 50.5 5.5 50.5 6.5 49.5 6.5 49.5 5.5</georss:polygon>")
                // spatial dataset identifier from the citation identifier
                assertContains(body, "<inspire_dls:spatial_dataset_identifier_code>dataset-1</inspire_dls:spatial_dataset_identifier_code>")
                assertContains(body, "<inspire_dls:spatial_dataset_identifier_namespace>https://example.org/datasets</inspire_dls:spatial_dataset_identifier_namespace>")
            }
        }

    @Test
    fun testServiceFeedNotFound() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns SearchResult(0, buildJsonArray { })
            client.get("/atom/service/unknown-uuid").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun testDatasetFeed() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
                SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("dataset-uuid-1", AtomTestHelpers.DATASET_IDF)) })

            client.get("/atom/dataset/service-uuid-1?datasetUuid=dataset-uuid-1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, "<title>Test Dataset</title>")
                assertContains(body, "<subtitle>Test dataset abstract</subtitle>")
                // download link with extension based mime type
                assertContains(body, """href="https://example.org/data/dataset-1.zip"""")
                assertContains(body, """type="application/zip"""")
                assertContains(body, """title="Dataset download"""")
                // parent service feed link
                assertContains(body, "/atom/service/service-uuid-1")
                assertContains(body, """rel="up"""")
                // use limitation as rights
                assertContains(body, "<rights>Dataset use limitation</rights>")
                // CRS categories from the reference system
                assertContains(body, """<category term="EPSG:25832" label="EPSG:25832"/>""")
                // the pure date is extended to a date-time
                assertContains(body, "<updated>2024-02-01T00:00:00Z</updated>")
            }
        }

    @Test
    fun testDatasetFeedNotFound() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns SearchResult(0, buildJsonArray { })
            client.get("/atom/dataset/service-uuid-1?datasetUuid=unknown").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun testGetDatasetRedirectsToSingleLink() =
        addWrapper(followRedirects = false) { client, esMock ->
            coEvery { esMock.search(any()) } returns
                SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("dataset-uuid-1", AtomTestHelpers.DATASET_IDF)) })

            client.get("/atom/get-dataset/service-uuid-1?datasetUuid=dataset-uuid-1").apply {
                assertEquals(HttpStatusCode.Found, status)
                assertContains(
                    headers["Location"].orEmpty(),
                    "https://example.org/data/dataset-1.zip",
                )
            }
        }

    @Test
    fun testGetDatasetWithMultipleLinksRendersFeed() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returns
                SearchResult(
                    1,
                    buildJsonArray {
                        add(AtomTestHelpers.esHit("dataset-uuid-1", DATASET_IDF_MULTI_LINK))
                    },
                )

            client.get("/atom/get-dataset/service-uuid-1?datasetUuid=dataset-uuid-1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, """href="https://example.org/data/dataset-1.zip"""")
                assertContains(body, """href="https://example.org/data/dataset-1.gml"""")
            }
        }

    @Test
    fun testOpensearchDescription() =
        addWrapper { client, esMock ->
            coEvery { esMock.search(any()) } returnsMany
                listOf(
                    // 1. lookup of the service document for the description
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("service-uuid-1", AtomTestHelpers.SERVICE_IDF)) }),
                    // 2. lookup of the service document when the service feed is produced
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("service-uuid-1", AtomTestHelpers.SERVICE_IDF)) }),
                    // 3. coupled resources of the service feed
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("dataset-uuid-1", AtomTestHelpers.DATASET_IDF)) }),
                    SearchResult(1, buildJsonArray { add(AtomTestHelpers.esHit("dataset-uuid-1", AtomTestHelpers.DATASET_IDF)) }),
                )

            client.get("/atom/opensearch-description/service-uuid-1").apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, "<OpenSearchDescription")
                assertContains(body, "<ShortName>ATOM Download</ShortName>")
                assertContains(body, "<Description>Test service abstract</Description>")
                assertContains(body, "<Contact>")
                assertContains(body, "/atom/opensearch-description/service-uuid-1")
                assertContains(body, "/atom/service/service-uuid-1?q={searchTerms}")
                assertContains(body, "/atom/dataset/service-uuid-1?spatial_dataset_identifier_code=")
                assertContains(body, "/atom/get-dataset/service-uuid-1?spatial_dataset_identifier_code=")
                // the coupled dataset is listed as example query
                assertContains(body, """<Query role="example"""")
                assertContains(body, """inspire_dls:spatial_dataset_identifier_code="dataset-1"""")
                assertContains(body, "<Language>de</Language>")
            }
        }

    @Test
    fun testUuidQueryTargetsObjectIdAndOrgObjectId() =
        addWrapper { client, esMock ->
            val captured = slot<String>()
            coEvery { esMock.search(capture(captured)) } returns SearchResult(0, buildJsonArray { })

            client.get("/atom/service/uuid-1").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
            val esQuery = Json.parseToJsonElement(captured.captured).jsonObject
            val queryString = esQuery.toString()
            // the search searches the document id and the org_obj_id field
            assertContains(queryString, """"terms":{"_id":["uuid-1"]}""")
            assertContains(queryString, """"t01_object.org_obj_id":["uuid-1"]""")
        }

    private fun addWrapper(
        followRedirects: Boolean = true,
        block: suspend ApplicationTestBuilder.(HttpClient, ElasticsearchService) -> Unit,
    ) {
        testApplication {
            val esMock = mockkClass(ElasticsearchService::class)
            val client = createClient { this.followRedirects = followRedirects }
            application {
                val searchService = AtomSearchService(esMock, config)
                val serviceFeedProducer = ServiceFeedProducer(searchService, config)
                dependencies {
                    provide<AtomSearchService> { searchService }
                    provide<ServiceFeedProducer> { serviceFeedProducer }
                    provide<DatasetFeedProducer> { DatasetFeedProducer(searchService, config) }
                    provide<OpensearchDescriptionProducer> {
                        OpensearchDescriptionProducer(searchService, serviceFeedProducer)
                    }
                }
                configureSwagger()
                configureAtomRouting()
            }

            block(client, esMock)
        }
    }
}

/** A sample IDF document with two download links. */
private val DATASET_IDF_MULTI_LINK: String =
    AtomTestHelpers.DATASET_IDF.replace(
        "</gmd:onLine>",
        """
        </gmd:onLine>
        <gmd:onLine>
            <gmd:CI_OnlineResource>
              <gmd:linkage><gmd:URL>https://example.org/data/dataset-1.gml</gmd:URL></gmd:linkage>
              <gmd:function><gmd:CI_OnLineFunctionCode codeListValue="download"/></gmd:function>
              <gmd:name><gco:CharacterString>GML download</gco:CharacterString></gmd:name>
            </gmd:CI_OnlineResource>
        </gmd:onLine>
        """.trimIndent(),
    )
