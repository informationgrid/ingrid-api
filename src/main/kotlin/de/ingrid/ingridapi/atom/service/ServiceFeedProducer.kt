package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.atom.model.AtomAuthor
import de.ingrid.ingridapi.atom.model.AtomCategory
import de.ingrid.ingridapi.atom.model.AtomLink
import de.ingrid.ingridapi.atom.model.ServiceFeed
import de.ingrid.ingridapi.atom.model.ServiceFeedEntry
import de.ingrid.ingridapi.atom.model.ServiceFeedList
import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.w3c.dom.Document
import org.w3c.dom.Node

/**
 * Produces the service feed (the Atom feed of a download service) and the service feed list
 * from the IDF metadata documents.
 *
 * This is the migration of `de.ingrid.iface.atomDownloadService.ServiceFeedProducer`,
 * `de.ingrid.iface.util.ServiceFeedUtils` and
 * `de.ingrid.iface.atomDownloadService.IGCCoupledResourcesServiceFeedEntryProducer`.
 *
 * The `CSWGetRecordByIdServiceFeedEntryProducer` of the legacy implementation resolves coupled
 * resources by fetching external CSW GetRecordById documents; this is not part of this
 * migration, only coupled resources available in the index are listed.
 */
class ServiceFeedProducer(
    private val searchService: AtomSearchService,
    private val config: AppConfig,
) {
    private val log = logger {}

    /** The XPath for download links inside an IDF document. */
    internal val downloadLinkXPath =
        "//gmd:transferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine[" +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='Download of data' or " +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='download' or " +
            ".//gmd:function/gmd:CI_OnLineFunctionCode/@codeListValue='Datendownload']"

    /** Produces the service feed for the given download service, or `null` if it does not exist. */
    suspend fun produce(
        request: ServiceFeedRequest,
        urlBuilder: AtomUrlBuilder,
    ): ServiceFeed? {
        log.debug { "Build service feed from IGC resource for service: ${request.uuid}" }
        val idfDoc = searchService.findDocumentByUuid(request.uuid) ?: return null

        val serviceFeed = createFromIdf(idfDoc, urlBuilder)

        // collect the entries from all entry producers
        val entryList = mutableListOf<ServiceFeedEntry>()
        entryList.addAll(produceIgcCoupledResources(idfDoc, serviceFeed, request, urlBuilder))

        // filter duplicate entries (coming from possibly external references that link to local ones)
        val uuids = mutableSetOf<String>()
        val uniqueEntries =
            entryList.filter { entry ->
                val uuid = entry.uuid
                uuid != null && uuids.add(uuid)
            }

        return serviceFeed.copy(entries = uniqueEntries)
    }

    /** Produces the service feed list for all download services matching the query. */
    suspend fun produceList(
        request: ServiceFeedListRequest,
        urlBuilder: AtomUrlBuilder,
    ): ServiceFeedList {
        log.debug { "Build service feed list for query: ${request.query}" }
        val documents = searchService.findDownloadServiceDocuments(request.query)
        val entries =
            documents.mapNotNull { idfDoc ->
                createFromIdf(idfDoc, urlBuilder)
            }
        val selfLink =
            AtomLink(
                href = urlBuilder.serviceFeedList(request.query),
                rel = "self",
                type = "application/atom+xml",
                hrefLang = "de",
                title = "Feed containing the service list",
            )
        return ServiceFeedList(selfReferencingLink = selfLink, entries = entries)
    }

    /**
     * Creates a service feed from the given IDF document.
     *
     * This is the migration of `de.ingrid.iface.util.ServiceFeedUtils.createFromIdf`.
     */
    internal fun createFromIdf(
        idfDoc: Document,
        urlBuilder: AtomUrlBuilder,
    ): ServiceFeed {
        val uuid = IdfXml.getString(idfDoc, "//gmd:fileIdentifier/gco:CharacterString")
        val selfHref = urlBuilder.serviceFeed(uuid.orEmpty())

        return ServiceFeed(
            uuid = uuid,
            title =
                IdfXml.getString(
                    idfDoc,
                    "//gmd:identificationInfo//gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString",
                ),
            subTitle = IdfXml.getString(idfDoc, "//gmd:identificationInfo//gmd:abstract/gco:CharacterString"),
            updated =
                IdfXml.getString(
                    idfDoc,
                    "//gmd:dateStamp/gco:DateTime | //gmd:dateStamp/gco:Date[not(../gco:DateTime)]",
                ),
            copyright = extractCopyright(idfDoc),
            metadataAccessUrl =
                AtomLink(
                    href = config.atomMetadataAccessUrl.replace("{uuid}", uuid.orEmpty()),
                    rel = "describedby",
                    type = "application/vnd.ogc.csw.GetRecordByIdResponse_xml",
                ),
            selfReferencingLink =
                AtomUrlBuilder.feedLink(
                    href = selfHref,
                    rel = "self",
                    title = "Feed containing the dataset (in one or more downloadable formats)",
                ),
            openSearchDefinitionLink =
                AtomLink(
                    href = urlBuilder.opensearchDescription(uuid.orEmpty()),
                    rel = "search",
                    type = "application/opensearchdescription+xml",
                    hrefLang = "de",
                    title = "Open Search Description",
                ),
            identifier = selfHref,
            author = extractAuthor(idfDoc),
        )
    }

    /**
     * Produces the service feed entries for the coupled resources of a download service that are
     * available in the index.
     *
     * This is the migration of `IGCCoupledResourcesServiceFeedEntryProducer.produce`.
     */
    internal suspend fun produceIgcCoupledResources(
        idfDoc: Document,
        serviceFeed: ServiceFeed,
        request: ServiceFeedRequest,
        urlBuilder: AtomUrlBuilder,
    ): List<ServiceFeedEntry> {
        val coupledDataUuids = IdfXml.getStringList(idfDoc, "//srv:operatesOn/@uuidref")
        val coupledOtherUuids =
            IdfXml.getStringList(
                idfDoc,
                "//idf:crossReference[./idf:attachedToField/@entry-id='9990' and ./idf:attachedToField/@list-id='2000']/@uuid",
            )
        val coupledUuids = (coupledDataUuids + coupledOtherUuids).distinct()
        log.debug { "Coupled resources found: ${coupledUuids.size}" }
        if (coupledUuids.isEmpty()) return emptyList()

        val documents = searchService.findCoupledResourceDocuments(coupledUuids, request.query)
        val entryList = mutableListOf<ServiceFeedEntry>()
        val processedUuids = mutableListOf<String>()

        for (doc in documents) {
            val uuid = IdfUtils.getRecordId(doc)
            if (uuid == null) {
                log.error { "Dataset did not have a record id" }
                continue
            }
            // do not process data with identical uuids (filter duplicates)
            if (processedUuids.contains(uuid)) {
                log.debug { "Found duplicate Record. Duplicate ID: '$uuid'." }
                continue
            }
            processedUuids.add(uuid)

            // check for data sets without data download links
            if (!IdfXml.nodeExists(doc, downloadLinkXPath)) {
                log.debug { "No Download Data Links found in coupled resource: $uuid" }
                continue
            }

            entryList.add(createEntry(doc, uuid, serviceFeed.uuid, urlBuilder))
        }
        return entryList
    }

    /** Creates a service feed entry from the IDF document of a coupled resource. */
    private fun createEntry(
        doc: Document,
        uuid: String,
        serviceFeedUuid: String?,
        urlBuilder: AtomUrlBuilder,
    ): ServiceFeedEntry {
        val datasetFeedHref = urlBuilder.datasetFeed(serviceFeedUuid.orEmpty(), uuid)
        return ServiceFeedEntry(
            uuid = uuid,
            title =
                IdfXml.getString(
                    doc,
                    "//gmd:identificationInfo//gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString",
                ),
            summary = IdfXml.getString(doc, "//gmd:identificationInfo//gmd:abstract/gco:CharacterString"),
            datasetMetadataRecord =
                AtomLink(
                    href = config.atomMetadataAccessUrl.replace("{uuid}", uuid),
                    rel = "describedby",
                    type = "application/vnd.ogc.csw.GetRecordByIdResponse_xml",
                ),
            datasetFeed =
                AtomUrlBuilder.feedLink(
                    href = datasetFeedHref,
                    rel = "alternate",
                ),
            datasetIdentifier = datasetFeedHref,
            updated =
                IdfXml.getString(doc, "//gmd:dateStamp/gco:DateTime | //gmd:dateStamp/gco:Date[not(../gco:DateTime)]"),
            rights = extractRights(doc),
            author = extractAuthor(doc),
            polygon = IdfUtils.getEnclosingBoundingBoxAsPolygon(doc),
            crs = extractReferenceSystems(doc),
        ).let { entry -> extractSpatialDatasetIdentifier(doc, entry) }
    }

    /**
     * Extracts the Spatial Dataset Unique Resource Identifier from the identifier of the
     * citation: the code is split into namespace and code at the last slash.
     */
    private fun extractSpatialDatasetIdentifier(
        doc: Document,
        entry: ServiceFeedEntry,
    ): ServiceFeedEntry {
        val identifierPath = "//gmd:identificationInfo//gmd:citation//gmd:identifier/gmd:MD_Identifier/"
        val code =
            IdfXml.getString(
                doc,
                identifierPath + "gmd:code/gco:CharacterString|" + identifierPath + "gmd:code/gmx:Anchor",
            ) ?: return entry
        var codeValue = code
        if (codeValue.endsWith("/")) {
            codeValue = codeValue.substring(0, codeValue.length - 1)
        }
        val lastSlash = codeValue.lastIndexOf('/')
        return if (lastSlash >= 0) {
            entry.copy(
                spatialDatasetIdentifierNamespace = codeValue.substring(0, lastSlash),
                spatialDatasetIdentifierCode = codeValue.substring(lastSlash + 1),
            )
        } else {
            entry.copy(spatialDatasetIdentifierCode = codeValue)
        }
    }

    /**
     * Extracts the copyright (access constraints) of a metadata document: the
     * `accessConstraints` code list values, and for `otherRestrictions` the
     * `otherConstraints` value.
     */
    internal fun extractCopyright(idfDoc: Document): String {
        val resourceConstraints =
            IdfXml.getNodeList(idfDoc, "//gmd:identificationInfo/*/gmd:resourceConstraints[*/gmd:accessConstraints]")
                ?: return ""
        val copyRight = StringBuilder()
        for (i in 0 until resourceConstraints.length) {
            val node = resourceConstraints.item(i)
            val restrictionCode = IdfXml.getString(node, "*/gmd:accessConstraints/*/@codeListValue").orEmpty()
            if (copyRight.isNotEmpty()) copyRight.append("; ")
            copyRight.append(restrictionCode)
            if (restrictionCode.equals("otherRestrictions", ignoreCase = true)) {
                val otherRestrictions = IdfXml.getString(node, "*/gmd:otherConstraints/gco:CharacterString")
                if (!otherRestrictions.isNullOrEmpty()) {
                    copyRight.append(": ").append(otherRestrictions)
                }
            }
        }
        return copyRight.toString()
    }

    /**
     * Extracts the rights (use limitations) of a metadata document.
     *
     * Note: the dataset feed only uses the useLimitations and ignores other restrictions
     * (as in the legacy implementation, REDMINE-348).
     */
    internal fun extractRights(doc: Document): String {
        val resourceConstraints = IdfXml.getNodeList(doc, "//gmd:identificationInfo/*/gmd:resourceConstraints") ?: return ""
        val rights = StringBuilder()
        for (i in 0 until resourceConstraints.length) {
            val useLimitation = IdfXml.getString(resourceConstraints.item(i), "*/gmd:useLimitation/gco:CharacterString")
            if (!useLimitation.isNullOrEmpty()) {
                if (rights.isNotEmpty()) rights.append("; ")
                rights.append(useLimitation)
            }
        }
        return rights.toString()
    }

    /** Extracts the author (point of contact) of a metadata document. */
    internal fun extractAuthor(doc: Document): AtomAuthor =
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

    /** Extracts the reference systems (CRSs) of a metadata document as categories. */
    internal fun extractReferenceSystems(doc: Document): List<AtomCategory> {
        val nodes =
            IdfXml.getNodeList(doc, "//gmd:referenceSystemInfo/gmd:MD_ReferenceSystem/gmd:referenceSystemIdentifier/gmd:RS_Identifier")
                ?: return emptyList()
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
}
