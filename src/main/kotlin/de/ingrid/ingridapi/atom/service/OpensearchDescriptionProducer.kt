package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.atom.model.OpensearchDescription
import de.ingrid.ingridapi.atom.model.OpensearchDescriptionUrl
import de.ingrid.ingridapi.atom.model.OpensearchQuery
import io.github.oshai.kotlinlogging.KotlinLogging.logger

/**
 * Produces the OpenSearch Description Document of a download service.
 *
 * This is the migration of
 * `de.ingrid.iface.atomDownloadService.OpensearchDescriptionProducer`.
 */
class OpensearchDescriptionProducer(
    private val searchService: AtomSearchService,
    private val serviceFeedProducer: ServiceFeedProducer,
) {
    private val log = logger {}

    /** Produces the OpenSearch description for the given download service, or `null` if it does not exist. */
    suspend fun produce(
        request: OpensearchDescriptionRequest,
        urlBuilder: AtomUrlBuilder,
    ): OpensearchDescription? {
        log.debug { "Build opensearch description for service: ${request.uuid}" }
        val idfDoc = searchService.findDocumentByUuid(request.uuid) ?: return null

        // make sure the short name is no longer than 16 chars, see INGRID-2351
        val shortName = "ATOM Download"

        val serviceFeedRequest = ServiceFeedRequest(uuid = request.uuid)
        val serviceFeed =
            serviceFeedProducer.produce(serviceFeedRequest, urlBuilder) ?: return null

        val examples =
            serviceFeed.entries.map { entry ->
                OpensearchQuery(
                    role = "example",
                    spatialDatasetIdentifierNamespace = entry.spatialDatasetIdentifierNamespace,
                    spatialDatasetIdentifierCode = entry.spatialDatasetIdentifierCode,
                    crs = entry.crs.firstOrNull()?.label,
                    language = "de",
                    title = entry.title,
                    count = 1,
                )
            }

        return OpensearchDescription(
            shortName = shortName,
            description =
                IdfXml.getString(
                    idfDoc,
                    "//gmd:identificationInfo//gmd:abstract/gco:CharacterString",
                ),
            selfReferencingUrlTemplate =
                OpensearchDescriptionUrl(
                    template = urlBuilder.opensearchDescription(request.uuid),
                    type = "application/opensearchdescription+xml",
                    rel = "self",
                ),
            resultsUrlTemplate =
                OpensearchDescriptionUrl(
                    template = urlBuilder.serviceFeedWithSearchTerms(request.uuid),
                    type = "text/html",
                    rel = "results",
                ),
            describeSpatialDatasetOperationUrlTemplate =
                OpensearchDescriptionUrl(
                    template = urlBuilder.describeSpatialDataset(request.uuid),
                    type = "application/atom+xml",
                    rel = "describedby",
                ),
            getSpatialDatasetOperationUrlTemplate =
                OpensearchDescriptionUrl(
                    template = urlBuilder.getSpatialDataset(request.uuid),
                    type = "application/atom+xml",
                    rel = "results",
                ),
            contact =
                IdfXml.getString(
                    idfDoc,
                    "//gmd:identificationInfo//gmd:pointOfContact//gmd:contactInfo/gmd:CI_Contact/gmd:address/gmd:CI_Address/gmd:electronicMailAddress/gco:CharacterString",
                ),
            longName = shortName,
            examples = examples,
            languages = listOf("de"),
        )
    }
}
