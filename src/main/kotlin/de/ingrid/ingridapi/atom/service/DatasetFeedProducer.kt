package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.atom.model.AtomAuthor
import de.ingrid.ingridapi.atom.model.AtomCategory
import de.ingrid.ingridapi.atom.model.AtomLink
import de.ingrid.ingridapi.atom.model.DatasetFeed
import de.ingrid.ingridapi.atom.model.DatasetFeedEntry
import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Produces the dataset feed (the Atom feed describing the downloadable files of a dataset).
 *
 * This is the migration of `de.ingrid.iface.atomDownloadService.DatasetFeedProducer`,
 * `de.ingrid.iface.atomDownloadService.DatasetFeedFactory` and
 * `de.ingrid.iface.atomDownloadService.DefaultDatasetFeedEntryProducer`.
 *
 * Deviation from the legacy implementation: datasets referenced by external CSW GetRecordById
 * URLs (coupled resources of external catalogs) are not fetched; the dataset is then reported
 * as not found.
 */
class DatasetFeedProducer(
    private val searchService: AtomSearchService,
    private val config: AppConfig,
) {
    private val log = logger {}

    /** XPath of the download links inside an IDF document. */
    private val downloadLinkXPath =
        "//gmd:transferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine[" +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='Download of data' or " +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='download' or " +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='Datendownload']"

    /** XPath of the system identifiers (reference systems) inside an IDF document. */
    private val systemIdentifierXPath =
        "//gmd:referenceSystemInfo/gmd:MD_ReferenceSystem/gmd:referenceSystemIdentifier/gmd:RS_Identifier"

    private val httpClient =
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = config.atomUrlConnectTimeout.toLong()
                requestTimeoutMillis = config.atomUrlReadTimeout.toLong()
            }
        }

    /** Produces the dataset feed for the given request, or `null` if the dataset was not found. */
    suspend fun produce(
        request: DatasetFeedRequest,
        urlBuilder: AtomUrlBuilder,
    ): DatasetFeed? {
        log.debug { "Build dataset feed for dataset: $request" }

        val doc = findDatasetFeedDocument(request) ?: return null

        val uuid = IdfXml.getString(doc, "//gmd:fileIdentifier/gco:CharacterString")
        val selfHref = urlBuilder.datasetFeed(request.serviceFeedUuid, uuid.orEmpty())

        val describedBy =
            listOf(
                AtomLink(
                    href = config.atomMetadataAccessUrl.replace("{uuid}", uuid.orEmpty()),
                    rel = "describedby",
                    type = "application/vnd.ogc.csw.GetRecordByIdResponse_xml",
                ),
            )

        val datasetFeed =
            DatasetFeed(
                uuid = uuid,
                title =
                    IdfXml.getString(
                        doc,
                        "//gmd:identificationInfo//gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString",
                    ),
                subTitle = IdfXml.getString(doc, "//gmd:identificationInfo//gmd:abstract/gco:CharacterString"),
                selfReferencingLink =
                    AtomUrlBuilder.feedLink(
                        href = selfHref,
                        rel = "self",
                    ),
                describedBy = describedBy,
                downloadServiceFeed =
                    AtomUrlBuilder.feedLink(
                        href = urlBuilder.serviceFeed(request.serviceFeedUuid),
                        rel = "up",
                        title = "The parent service feed document.",
                    ),
                identifier = selfHref,
                rights = extractUseLimitations(doc),
                updated =
                    IdfXml.getString(doc, "//gmd:dateStamp/gco:DateTime | //gmd:dateStamp/gco:Date[not(../gco:DateTime)]"),
                author = extractAuthor(doc),
                entries = produceDatasetEntries(doc),
            )

        // add link to portal detail page if requested
        if (request.detail && config.atomMetadataDetailsUrl.isNotEmpty()) {
            val detailUrl = config.atomMetadataDetailsUrl
            return datasetFeed.copy(
                detailLink =
                    AtomLink(
                        rel = "detail",
                        href = "$detailUrl?docuuid=$uuid",
                        type = "html",
                    ),
            )
        }

        return datasetFeed
    }

    /**
     * Finds the IDF document of the requested dataset.
     *
     * This is the migration of `DatasetFeedFactory.getDatasetFeedDocument`, without the external
     * CSW GetRecordById support: when the `datasetUuid` parameter is a GetRecordById URL, the
     * dataset is reported as not found.
     */
    private suspend fun findDatasetFeedDocument(request: DatasetFeedRequest): Document? {
        val datasetUuid = request.datasetFeedUuid
        if (datasetUuid != null && datasetUuid.lowercase().contains("request=getrecordbyid")) {
            log.warn { "External CSW GetRecordById URLs are not supported, ignoring dataset: $datasetUuid" }
            return null
        }
        if (!datasetUuid.isNullOrEmpty()) {
            log.debug { "Found IGC dataset UUID: $datasetUuid" }
            return searchService.findDocumentByUuid(datasetUuid)
        }
        if (!request.spatialDatasetIdentifierCode.isNullOrEmpty()) {
            log.debug {
                "Searching dataset by spatial dataset identifier: " +
                    "${request.spatialDatasetIdentifierNamespace}/${request.spatialDatasetIdentifierCode}"
            }
            return searchService
                .findDatasetDocumentsBySpatialIdentifier(
                    code = request.spatialDatasetIdentifierCode,
                    namespace = request.spatialDatasetIdentifierNamespace,
                    crs = request.crs,
                    language = request.language,
                ).firstOrNull()
        }
        return null
    }

    /**
     * Extracts the use limitations of a metadata document (the rights of the dataset feed).
     * Only useLimitations are used and other restrictions are ignored (REDMINE-348).
     */
    private fun extractUseLimitations(doc: Document): String {
        val resourceConstraints = IdfXml.getNodeList(doc, "//gmd:identificationInfo/*/gmd:resourceConstraints") ?: return ""
        val rights = StringBuilder()
        for (i in 0 until resourceConstraints.length) {
            val useLimitation =
                IdfXml.getString(resourceConstraints.item(i), "*/gmd:useLimitation/gco:CharacterString")
            if (!useLimitation.isNullOrEmpty()) {
                if (rights.isNotEmpty()) rights.append("; ")
                rights.append(useLimitation)
            }
        }
        return rights.toString()
    }

    private fun extractAuthor(doc: Document): AtomAuthor =
        AtomAuthor(
            name =
                IdfXml.getString(
                    doc,
                    "//gmd:identificationInfo//gmd:pointOfContact//gmd:organisationName/gco:CharacterString",
                ),
            email =
                IdfXml.getString(
                    doc,
                    "//gmd:identificationInfo//gmd:pointOfContact//gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:electronicMailAddress/gco:CharacterString",
                ),
        )

    /**
     * Produces the dataset feed entries from the download links of the metadata document.
     *
     * This is the migration of `DefaultDatasetFeedEntryProducer.produce`.
     */
    internal suspend fun produceDatasetEntries(doc: Document): List<DatasetFeedEntry> {
        val results = mutableListOf<DatasetFeedEntry>()
        val linkages = IdfXml.getNodeList(doc, downloadLinkXPath) ?: return results

        for (i in 0 until linkages.length) {
            val linkage = linkages.item(i) ?: continue
            val url = IdfXml.getString(linkage, ".//gmd:linkage/gmd:URL") ?: continue
            val type = MimeTypes.detect(url)

            if (type == "application/atom+xml") {
                // produce multiple entries by atom feeds or relative urls
                results.addAll(produceEntriesByAtomOrXml(url))
            } else {
                // produce a single entry by its type
                results.add(produceEntry(url, type, linkage, doc))
            }
        }
        return results
    }

    /** Creates a single dataset feed entry from a download link. */
    private fun produceEntry(
        url: String,
        type: String,
        linkage: Node,
        doc: Document,
    ): DatasetFeedEntry {
        // set link title
        val name = IdfXml.getString(linkage, ".//gmd:name//gco:CharacterString") ?: url

        val entry =
            DatasetFeedEntry(
                title = name,
                links =
                    listOf(
                        AtomLink(
                            href = url,
                            type = type,
                            rel = "alternate",
                            title = name,
                        ),
                    ),
                id = url,
            )

        // set entry categories
        val categories = extractCategories(doc)
        return if (categories.isNotEmpty()) entry.copy(crs = categories) else entry
    }

    /** Extracts the reference systems of the document as categories. */
    private fun extractCategories(doc: Document): List<AtomCategory> {
        val nodes = IdfXml.getNodeList(doc, systemIdentifierXPath) ?: return emptyList()
        val categories = mutableListOf<AtomCategory>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val refSystemCode = IdfXml.getString(node, "gmd:code/gco:CharacterString|gmd:code/gmx:Anchor")
            val epsgNumber = extractEpsgCodeNumber(refSystemCode)
            categories.add(
                AtomCategory(
                    term = if (epsgNumber != null) "EPSG:$epsgNumber" else IdfXml.getString(node, "gmd:codeSpace/gco:CharacterString"),
                    label = refSystemCode,
                ),
            )
        }
        return categories
    }

    /**
     * The given url of an atom or xml document is recursively dissolved until the downloadable
     * entries are found. All entries are grouped in a list and returned.
     *
     * This is a special handling for atom or xml documents that have child atom(s) or xml(s).
     *
     * This is the migration of `DefaultDatasetFeedEntryProducer.produceEntriesByAtomOrXml`.
     */
    private suspend fun produceEntriesByAtomOrXml(url: String): List<DatasetFeedEntry> {
        val entries = mutableListOf<DatasetFeedEntry>()
        val urls = mutableListOf(url)

        // when a child atom or xml is found, it is added back to the urls for dissolving
        while (urls.isNotEmpty()) {
            val currentUrl = urls.removeAt(0)
            val doc = fetchXmlDocument(currentUrl) ?: continue

            val nodeList = doc.getElementsByTagName("entry")
            for (i in 0 until nodeList.length) {
                try {
                    val entryEl = nodeList.item(i) as? Element ?: continue
                    val linkEl = getLinkElByEntry(entryEl) ?: continue
                    val type = linkEl.getAttribute("type")
                    var href = linkEl.getAttribute("href")
                    if (isRelativePath(href)) href = getBaseUrl(currentUrl) + href

                    // check if entry is a child atom or xml
                    if (type == "application/atom+xml") {
                        // add url back to dissolving process
                        urls.add(href)
                        continue
                    }

                    // create downloadable entry
                    val title = getDownloadTitleByEntry(entryEl) ?: href

                    val categories =
                        entryEl.getElementsByTagName("category").let { catNodes ->
                            (0 until catNodes.length).mapNotNull { index ->
                                val catEl = catNodes.item(index) as? Element ?: return@mapNotNull null
                                AtomCategory(
                                    term = catEl.getAttribute("term").takeIf { it.isNotEmpty() },
                                    label = catEl.getAttribute("label").takeIf { it.isNotEmpty() },
                                )
                            }
                        }

                    entries.add(
                        DatasetFeedEntry(
                            title = title,
                            links =
                                listOf(
                                    AtomLink(
                                        href = href,
                                        rel = "alternate",
                                        type = type,
                                        title = title,
                                    ),
                                ),
                            id = href,
                            crs = categories,
                        ),
                    )
                } catch (ex: Exception) {
                    log.error(ex) { "Error processing entry of atom feed $currentUrl" }
                }
            }
        }
        return entries
    }

    /** Fetches an XML document from the given URL, or `null` on error. */
    private suspend fun fetchXmlDocument(url: String): Document? =
        try {
            val body = httpClient.get(url).bodyAsText()
            IdfXml.parse(body)
        } catch (ex: Exception) {
            log.warn { "Unable to obtain XML document from $url: ${ex.message}" }
            null
        }

    /**
     * Gets the download title of a given entry. The target attributes can be optional, the
     * title is therefore extracted by priority: title > link@title > id -> unknown.
     */
    private fun getDownloadTitleByEntry(entry: Element): String? {
        // get from title
        getValueFromFirstEntryByTag("title", entry)?.let { return it }

        // get from link
        val links = entry.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val link = links.item(i) as? Element ?: continue
            val title = link.getAttribute("title")
            if (title.isNotEmpty()) return title
        }

        // get from id
        return getValueFromFirstEntryByTag("id", entry) ?: "unknown"
    }

    /** The value of the first element with the given tag inside the given element, or `null`. */
    private fun getValueFromFirstEntryByTag(
        tag: String,
        el: Element,
    ): String? {
        val tags = el.getElementsByTagName(tag)
        if (tags.length == 0) return null
        val text = tags.item(0)?.textContent ?: return null
        // remove cdata if present
        return text.replace("<![CDATA[", "").replace("]]>", "").takeIf { it.isNotEmpty() }
    }

    private fun getLinkElByEntry(nodeEl: Element): Element? {
        val links = nodeEl.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val link = links.item(i) as? Element ?: continue
            val rel = link.getAttribute("rel")
            if (rel != "alternate") continue
            return link
        }
        return null
    }

    private fun isRelativePath(url: String): Boolean = !url.contains("http://") && !url.contains("https://")

    private fun getBaseUrl(url: String): String {
        val breakpoint = url.lastIndexOf("/")
        return if (breakpoint != -1) url.substring(0, breakpoint + 1) else url
    }
}
