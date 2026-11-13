package de.ingrid.ingridapi.atom

import de.ingrid.ingridapi.atom.service.AtomUrlBuilder
import de.ingrid.ingridapi.atom.service.IdfUtils
import de.ingrid.ingridapi.atom.service.IdfXml
import de.ingrid.ingridapi.atom.service.MimeTypes
import de.ingrid.ingridapi.atom.service.assureDateTime
import de.ingrid.ingridapi.atom.service.extractEpsgCodeNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomServiceTest {

    @Test
    fun testAssureDateTime() {
        assertEquals("2024-02-01T00:00:00Z", assureDateTime("2024-02-01"))
        assertEquals("2024-01-31T12:00:00", assureDateTime("2024-01-31T12:00:00"))
        assertEquals("", assureDateTime(null))
    }

    @Test
    fun testExtractEpsgCodeNumber() {
        assertEquals("25832", extractEpsgCodeNumber("EPSG:25832"))
        assertEquals("25832", extractEpsgCodeNumber("urn:ogc:def:crs:EPSG::25832"))
        assertEquals("4326", extractEpsgCodeNumber("epsg:4326 "))
        assertNull(extractEpsgCodeNumber("no code"))
        assertNull(extractEpsgCodeNumber(null))
    }

    @Test
    fun testMimeTypes() {
        assertEquals("application/atom+xml", MimeTypes.detect("https://example.org/feed.atom"))
        assertEquals("application/xml", MimeTypes.detect("https://example.org/feed.xml"))
        assertEquals("application/zip", MimeTypes.detect("https://example.org/data/dataset-1.zip"))
        assertEquals("application/gml+xml", MimeTypes.detect("https://example.org/data/dataset-1.gml?param=1"))
        assertEquals("application/octet-stream", MimeTypes.detect("https://example.org/data/file.unknownext"))
        assertEquals("application/octet-stream", MimeTypes.detect("https://example.org/data/noextension"))
    }

    @Test
    fun testParseIdfDocumentAndExtractMetadata() {
        val doc = IdfXml.parse(AtomTestHelpers.SERVICE_IDF)

        assertEquals("service-uuid-1", IdfXml.getString(doc, "//gmd:fileIdentifier/gco:CharacterString"))
        assertEquals(
            "Test Download Service",
            IdfXml.getString(doc, "//gmd:identificationInfo//gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString"),
        )
        assertEquals(
            listOf("dataset-uuid-1"),
            IdfXml.getStringList(doc, "//srv:operatesOn/@uuidref"),
        )
        assertTrue(IdfXml.nodeExists(doc, "//gmd:fileIdentifier"))
        assertFalse(IdfXml.nodeExists(doc, "//gmd:doesNotExist"))
    }

    @Test
    fun testEnclosingBoundingBoxAsPolygon() {
        val doc = IdfXml.parse(AtomTestHelpers.DATASET_IDF)
        // west=5.5, east=6.5, south=49.5, north=50.5 -> 10 polygon values
        assertEquals(
            listOf(49.5, 5.5, 50.5, 5.5, 50.5, 6.5, 49.5, 6.5, 49.5, 5.5),
            IdfUtils.getEnclosingBoundingBoxAsPolygon(doc),
        )
    }

    @Test
    fun testUrlBuilder() {
        val urlBuilder = AtomUrlBuilder("http://localhost:8081")
        assertEquals("http://localhost:8081/atom/service/uuid-1", urlBuilder.serviceFeed("uuid-1"))
        assertEquals(
            "http://localhost:8081/atom/service-list?q=wasser",
            urlBuilder.serviceFeedList("wasser"),
        )
        assertEquals("http://localhost:8081/atom/service-list", urlBuilder.serviceFeedList(null))
        assertEquals(
            "http://localhost:8081/atom/dataset/service-1?datasetUuid=dataset-1",
            urlBuilder.datasetFeed("service-1", "dataset-1"),
        )
        assertEquals(
            "http://localhost:8081/atom/get-dataset/service-1" +
                "?spatial_dataset_identifier_code={inspire_dls:spatial_dataset_identifier_code?}" +
                "&spatial_dataset_identifier_namespace={inspire_dls:spatial_dataset_identifier_namespace?}" +
                "&crs={inspire_dls:crs?}" +
                "&language={language?}",
            urlBuilder.getSpatialDataset("service-1"),
        )
        assertEquals(
            "http://localhost:8081/atom/opensearch-description/service-1",
            urlBuilder.opensearchDescription("service-1"),
        )
    }
}
