package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.core.services.asSafeString
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

/**
 * Escapes a value for the use inside XML text and attribute values.
 */
internal fun xmlEscape(value: String?): String =
    value
        .orEmpty()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

/**
 * Ensures that a date string from the metadata is a valid Atom date-time. Pure dates
 * (`2024-01-31`) are extended to a date-time (`2024-01-31T00:00:00Z`).
 */
internal fun assureDateTime(dateStr: String?): String {
    val result = dateStr.orEmpty()
    return if (result.isNotEmpty() && !result.contains("T")) "$result" + "T00:00:00Z" else result
}

/** Extracts the EPSG number out of a reference system code like `EPSG:25832` or an URN. */
internal fun extractEpsgCodeNumber(value: String?): String? {
    val input = value.orEmpty().lowercase()
    val regex = Regex("""epsg\D*(\d{4,5})\D?""")
    return regex.find(input)?.groupValues?.getOrNull(1)
}

/**
 * Prepares a string to be a valid path segment.
 */
internal fun encodeForPath(path: String?): String =
    java.net.URLEncoder.encode(path.orEmpty(), Charsets.UTF_8)
        .replace("+", "%20")

/** The namespace context of InGrid IDF documents. */
private object IdfNamespaceContext : NamespaceContext {
    val namespaces =
        mapOf(
            "idf" to "http://www.portalu.de/IDF/1.0",
            "gmd" to "http://www.isotc211.org/2005/gmd",
            "gco" to "http://www.isotc211.org/2005/gco",
            "gmx" to "http://www.isotc211.org/2005/gmx",
            "srv" to "http://www.isotc211.org/2005/srv",
            "gts" to "http://www.isotc211.org/2005/gts",
            "xlink" to "http://www.w3.org/1999/xlink",
        )

    override fun getNamespaceURI(prefix: String): String? = namespaces[prefix]

    override fun getPrefix(namespaceURI: String): String? =
        namespaces.entries.firstOrNull { it.value == namespaceURI }?.key

    override fun getPrefixes(namespaceURI: String): Iterator<String> =
        namespaces.entries.filter { it.value == namespaceURI }.map { it.key }.iterator()
}

/**
 * Parses IDF documents and evaluates XPath expressions on them.
 *
 * This is the migration of `de.ingrid.utils.xpath.XPathUtils` with the
 * `de.ingrid.utils.xml.IDFNamespaceContext` (ingrid-utils), reduced to the operations the
 * ATOM Download Service uses.
 */
object IdfXml {

    private val xPathFactory: XPathFactory = XPathFactory.newInstance()

    /** Parses the given XML string into a namespace-aware DOM document (XXE-safe). */
    fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xml)))
    }

    /** Returns the string value of the first node matching the XPath, or `null` if empty. */
    fun getString(
        document: Document,
        expression: String,
    ): String? {
        val value = evaluateString(document, expression)
        return value.ifEmpty { null }
    }

    /** Returns the string values of all nodes matching the XPath. */
    fun getStringList(
        document: Document,
        expression: String,
    ): List<String> {
        val nodes = evaluateNodeList(document, expression) ?: return emptyList()
        return (0 until nodes.length).mapNotNull { nodes.item(it)?.textContent?.takeIf(String::isNotEmpty) }
    }

    /** Returns all nodes matching the XPath, or `null` if the evaluation fails. */
    fun getNodeList(
        document: Document,
        expression: String,
    ): NodeList? = evaluateNodeList(document, expression)

    /** Returns whether at least one node matches the XPath. */
    fun nodeExists(
        document: Document,
        expression: String,
    ): Boolean = evaluateNodeList(document, expression)?.length?.let { it > 0 } ?: false

    /** Returns the string value of the first node matching the relative XPath, or `null`. */
    fun getString(
        node: Node,
        expression: String,
    ): String? =
        try {
            (newXPath().evaluate(expression, node, XPathConstants.STRING) as String).ifEmpty { null }
        } catch (_: Exception) {
            null
        }

    /** Returns all nodes matching the relative XPath, or `null` if the evaluation fails. */
    fun getNodeList(
        node: Node,
        expression: String,
    ): NodeList? =
        try {
            newXPath().evaluate(expression, node, XPathConstants.NODESET) as NodeList
        } catch (_: Exception) {
            null
        }

    private fun newXPath(): XPath =
        xPathFactory.newXPath().apply { namespaceContext = IdfNamespaceContext }

    private fun evaluateString(
        document: Document,
        expression: String,
    ): String =
        try {
            newXPath().evaluate(expression, document, XPathConstants.STRING) as String
        } catch (_: Exception) {
            ""
        }

    private fun evaluateNodeList(
        document: Document,
        expression: String,
    ): NodeList? =
        try {
            newXPath().evaluate(expression, document, XPathConstants.NODESET) as NodeList
        } catch (_: Exception) {
            null
        }
}

/**
 * Helpers to extract spatial information and identifiers from IDF documents.
 *
 * This is the migration of `de.ingrid.iface.util.IdfUtils`.
 */
object IdfUtils {

    /** XPath of the record id inside an IDF document. */
    private const val ID_XPATH = "idf:html/idf:body/idf:idfMdMetadata/gmd:fileIdentifier/gco:CharacterString"

    /** Extracts the id from the idf document. */
    fun getRecordId(document: Document): String? = IdfXml.getString(document, ID_XPATH)

    /**
     * Returns the largest BBOX of an IDF document as stated in `gmd:EX_GeographicBoundingBox`
     * as a list of polygon values. The result contains 10 values describing the bbox as a
     * polygon (south, west, north, west, north, east, south, east, south, west).
     *
     * In case the BBOX is just one point, the polygon only consists of 2 values
     * (0 - longitude, 1 - latitude).
     */
    fun getEnclosingBoundingBoxAsPolygon(document: Document): List<Double> {
        val bbox = getEnclosingBoundingBox(document)
        return if (bbox[0] == bbox[1] && bbox[2] == bbox[3]) {
            listOf(bbox[2], bbox[0])
        } else {
            listOf(
                bbox[2], bbox[0],
                bbox[3], bbox[0],
                bbox[3], bbox[1],
                bbox[2], bbox[1],
                bbox[2], bbox[0],
            )
        }
    }

    /**
     * Detects the largest BBOX of an IDF document as stated in `gmd:EX_GeographicBoundingBox`.
     *
     * @return An array of doubles where the values are: 0-west, 1-east, 2-south, 3-north
     */
    fun getEnclosingBoundingBox(document: Document): DoubleArray {
        val west =
            IdfXml.getStringList(
                document,
                "//gmd:identificationInfo//gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:westBoundLongitude/gco:Decimal",
            )
        val east =
            IdfXml.getStringList(
                document,
                "//gmd:identificationInfo//gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:eastBoundLongitude/gco:Decimal",
            )
        val south =
            IdfXml.getStringList(
                document,
                "//gmd:identificationInfo//gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:southBoundLatitude/gco:Decimal",
            )
        val north =
            IdfXml.getStringList(
                document,
                "//gmd:identificationInfo//gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox/gmd:northBoundLatitude/gco:Decimal",
            )

        var outerWest: Double? = null
        var outerEast: Double? = null
        var outerSouth: Double? = null
        var outerNorth: Double? = null
        for (i in west.indices) {
            val w = west.getOrNull(i)?.toDoubleOrNull() ?: continue
            outerWest = (outerWest ?: w).coerceAtMost(w)
            val e = east.getOrNull(i)?.toDoubleOrNull() ?: continue
            outerEast = (outerEast ?: e).coerceAtLeast(e)
            val n = north.getOrNull(i)?.toDoubleOrNull() ?: continue
            outerNorth = (outerNorth ?: n).coerceAtLeast(n)
            val s = south.getOrNull(i)?.toDoubleOrNull() ?: continue
            outerSouth = (outerSouth ?: s).coerceAtMost(s)
        }

        return doubleArrayOf(
            outerWest ?: 0.0,
            outerEast ?: 0.0,
            outerSouth ?: 0.0,
            outerNorth ?: 0.0,
        )
    }
}
