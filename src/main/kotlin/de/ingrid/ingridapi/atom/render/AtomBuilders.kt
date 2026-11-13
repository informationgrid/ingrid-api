package de.ingrid.ingridapi.atom.render

import de.ingrid.ingridapi.atom.model.AtomCategory
import de.ingrid.ingridapi.atom.model.AtomLink
import de.ingrid.ingridapi.atom.model.DatasetFeed
import de.ingrid.ingridapi.atom.model.DatasetFeedEntry
import de.ingrid.ingridapi.atom.model.OpensearchDescription
import de.ingrid.ingridapi.atom.model.ServiceFeed
import de.ingrid.ingridapi.atom.model.ServiceFeedEntry
import de.ingrid.ingridapi.atom.model.ServiceFeedList
import de.ingrid.ingridapi.atom.service.assureDateTime
import de.ingrid.ingridapi.atom.service.xmlEscape

/**
 * Renders the Atom feeds and the OpenSearch Description Document of the ATOM Download Service.
 *
 * This is the migration of the Atom builders of ingrid-interface-search
 * (`ServiceFeedListAtomBuilder`, `ServiceFeedAtomBuilder`, `DatasetFeedAtomBuilder`,
 * `DatasetAtomBuilder` and `OpensearchDescriptionBuilder`). The special handling for
 * Internet Explorer user agents of the legacy implementation is not migrated.
 */
object AtomBuilders {

    /** Renders the Atom feed listing all download services. */
    fun buildServiceFeedList(serviceFeedList: ServiceFeedList): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append(
                "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:georss=\"http://www.georss.org/georss\" " +
                    "xmlns:inspire_dls=\"http://inspire.ec.europa.eu/schemas/inspire_dls/1.0\" xml:lang=\"de\">\n",
            )
            // feed title
            append("<title>ATOM download service feeds</title>\n")
            // self-referencing link to this feed
            val self = serviceFeedList.selfReferencingLink
            if (self != null) {
                append(renderLink(self))
            }
            // identifier
            append("<id>${xmlEscape(self?.href)}</id>\n")

            for (entry in serviceFeedList.entries) {
                append("<entry>\n")
                // title service feed
                append("<title>${xmlEscape(entry.title)}</title>\n")
                // summary service feed
                append("<summary>${xmlEscape(entry.subTitle)}</summary>\n")
                // link to Service Feed
                val selfLink = entry.selfReferencingLink
                if (selfLink != null) {
                    append(renderLink(selfLink.copy(rel = "alternate")))
                }
                // link to download service ISO 19139 metadata
                entry.metadataAccessUrl?.let { append(renderLink(it)) }
                // link to Open Search definition file for this service
                entry.openSearchDefinitionLink?.let { append(renderLink(it)) }
                // identifier
                append("<id>${xmlEscape(selfLink?.href)}</id>\n")
                // rights, access restrictions
                append("<rights>${xmlEscape(entry.copyright)}</rights>\n")
                // date/time this feed was last updated
                append("<updated>${assureDateTime(entry.updated)}</updated>\n")
                // author contact information
                append(renderAuthor(entry.author))

                append("</entry>\n")
            }
            append("</feed>\n")
        }

    /** Renders the Atom feed of a single download service. */
    fun buildServiceFeed(serviceFeed: ServiceFeed): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append(
                "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:georss=\"http://www.georss.org/georss\" " +
                    "xmlns:inspire_dls=\"http://inspire.ec.europa.eu/schemas/inspire_dls/1.0\" xml:lang=\"de\">\n",
            )
            // feed title
            append("<title>${xmlEscape(serviceFeed.title)}</title>\n")
            // feed subtitle
            append("<subtitle>${xmlEscape(serviceFeed.subTitle)}</subtitle>\n")
            // link to download service ISO 19139 metadata
            serviceFeed.metadataAccessUrl?.let { append(renderLink(it)) }
            // self-referencing link to this feed
            serviceFeed.selfReferencingLink?.let { append(renderLink(it)) }
            // link to Open Search definition file for this service
            serviceFeed.openSearchDefinitionLink?.let { append(renderLink(it)) }
            // identifier
            append("<id>${xmlEscape(serviceFeed.selfReferencingLink?.href)}</id>\n")
            // rights, access restrictions
            append("<rights>${xmlEscape(serviceFeed.copyright)}</rights>\n")
            // date/time this feed was last updated
            append("<updated>${assureDateTime(serviceFeed.updated)}</updated>\n")
            // author contact information
            append(renderAuthor(serviceFeed.author))

            for (entry in serviceFeed.entries) {
                append("<entry>\n")
                // title dataset feed
                append("<title>${xmlEscape(entry.title)}</title>\n")
                // link to Dataset Feed
                entry.datasetFeed?.let { append(renderLink(it)) }
                // link to dataset metadata record
                entry.datasetMetadataRecord?.let { append(renderLink(it)) }
                // identifier for "Dataset Feed" for pre-defined dataset
                append("<id>${xmlEscape(entry.datasetIdentifier)}</id>\n")
                // rights, access info for pre-defined dataset
                append("<rights>${xmlEscape(entry.rights)}</rights>\n")
                // last date/time this entry was updated
                append("<updated>${assureDateTime(entry.updated)}</updated>\n")
                // summary
                append("<summary>${xmlEscape(entry.summary)}</summary>\n")
                if (entry.polygon.isNotEmpty()) {
                    // optional GeoRSS-Simple polygon outlining the bounding box of the pre-defined
                    // dataset described by the entry. Must be lat lon
                    append("<georss:polygon>")
                    append(entry.polygon.joinToString(" "))
                    append("</georss:polygon>\n")
                }
                if (entry.crs.isNotEmpty()) {
                    // CRSs in which the pre-defined Dataset is available
                    entry.crs.forEach { append(renderCategory(it)) }
                }
                // Spatial Dataset Unique Resource Identifier for this dataset
                if (entry.spatialDatasetIdentifierCode != null) {
                    append(
                        "<inspire_dls:spatial_dataset_identifier_code>" +
                            "${xmlEscape(entry.spatialDatasetIdentifierCode)}</inspire_dls:spatial_dataset_identifier_code>\n",
                    )
                    if (entry.spatialDatasetIdentifierNamespace != null) {
                        append(
                            "<inspire_dls:spatial_dataset_identifier_namespace>" +
                                "${xmlEscape(entry.spatialDatasetIdentifierNamespace)}</inspire_dls:spatial_dataset_identifier_namespace>\n",
                        )
                    }
                }
                append("</entry>\n")
            }
            append("</feed>\n")
        }

    /** Renders the "Dataset Feed" describing the downloadable files of a dataset. */
    fun buildDatasetFeed(datasetFeed: DatasetFeed): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:georss=\"http://www.georss.org/georss\" xml:lang=\"de\">\n")
            // feed title
            append("<title>${xmlEscape(datasetFeed.title)}</title>\n")
            // feed subtitle
            append("<subtitle>${xmlEscape(datasetFeed.subTitle)}</subtitle>\n")
            // link to ISO 19139 metadata
            datasetFeed.describedBy.forEach { append(renderLink(it)) }
            // self-referencing link to this feed
            datasetFeed.selfReferencingLink?.let { append(renderLink(it)) }
            // upward link to the corresponding download service feed
            datasetFeed.downloadServiceFeed?.let { append(renderLink(it)) }
            datasetFeed.detailLink?.let {
                // detail-link to portal
                append(renderLink(it, type = "html"))
            }
            // identifier
            append("<id>${xmlEscape(datasetFeed.selfReferencingLink?.href)}</id>\n")
            // rights, access restrictions
            append("<rights>${xmlEscape(datasetFeed.rights)}</rights>\n")
            // date/time this feed was last updated
            append("<updated>${assureDateTime(datasetFeed.updated)}</updated>\n")
            // author contact information
            append(renderAuthor(datasetFeed.author))

            for (entry in datasetFeed.entries) {
                append("<entry>\n")
                append("<title>${xmlEscape(entry.title)}</title>\n")
                // file download links
                entry.links.forEach { append(renderLink(it)) }
                append("<id>${xmlEscape(entry.id)}</id>\n")
                // date/time this feed was last updated
                append("<updated>${assureDateTime(datasetFeed.updated)}</updated>\n")
                if (entry.crs.isNotEmpty()) {
                    // CRSs in which the pre-defined Dataset is available
                    entry.crs.forEach { append(renderCategory(it)) }
                }
                append("</entry>\n")
            }
            append("</feed>\n")
        }

    /**
     * Renders the feed of the download links of a dataset, as response of the get-dataset
     * endpoint (used when a dataset has more than one download link; with a single link the
     * endpoint redirects to it directly).
     */
    fun buildDataset(datasetFeed: DatasetFeed): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            // Example "Dataset Feed"
            append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:lang=\"de\">\n")
            // feed title
            append("<title>${xmlEscape(datasetFeed.title)}</title>\n")
            // identifier
            append("<id>${xmlEscape(datasetFeed.uuid)}</id>\n")
            // date/time this feed was last updated
            append("<updated>${assureDateTime(datasetFeed.updated)}</updated>\n")

            for (entry in datasetFeed.entries) {
                append("<entry>\n")
                append("<title>${xmlEscape(entry.title)}</title>\n")
                append("<id>${xmlEscape(entry.id)}</id>\n")
                // file download links
                entry.links.forEach { append(renderLink(it)) }
                append("<updated>${assureDateTime(datasetFeed.updated)}</updated>\n")
                if (entry.crs.isNotEmpty()) {
                    // CRSs in which the pre-defined Dataset is available
                    entry.crs.forEach { append(renderCategory(it)) }
                }
                append("</entry>\n")
            }
            append("</feed>\n")
        }

    /** Renders the OpenSearch Description Document of a download service. */
    fun buildOpensearchDescription(description: OpensearchDescription): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append(
                "<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\"\n" +
                    "xmlns:inspire_dls=\"http://inspire.ec.europa.eu/schemas/inspire_dls/1.0\"\n" +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "xsi:schemaLocation=\"http://a9.com/-/spec/opensearch/1.1/ OpenSearch.xsd\">\n",
            )
            append("<ShortName>${xmlEscape(description.shortName)}</ShortName>\n")
            append("<Description>${xmlEscape(description.description)}</Description>\n")
            // URL of this document
            description.selfReferencingUrlTemplate?.let { append(renderUrlTemplate(it)) }
            // Generic URL template for browser integration
            description.resultsUrlTemplate?.let { append(renderUrlTemplate(it)) }
            // Describe Spatial Data Set Operation request URL template to be used in order to
            // retrieve the description of Spatial Object Types in a Spatial Dataset
            description.describeSpatialDatasetOperationUrlTemplate?.let { append(renderUrlTemplate(it)) }
            // Get Spatial Data Set Operation request URL template to be used in order to
            // retrieve a Spatial Dataset
            description.getSpatialDatasetOperationUrlTemplate?.let { append(renderUrlTemplate(it)) }
            append("<Contact>${xmlEscape(description.contact)}</Contact>\n")
            append("<LongName>${xmlEscape(description.longName)}</LongName>\n")

            for (query in description.examples) {
                append(
                    "<Query role=\"${xmlEscape(query.role)}\"" +
                        " inspire_dls:spatial_dataset_identifier_namespace=\"${xmlEscape(query.spatialDatasetIdentifierNamespace)}\"" +
                        " inspire_dls:spatial_dataset_identifier_code=\"${xmlEscape(query.spatialDatasetIdentifierCode)}\"",
                )
                if (query.crs != null) {
                    append(" inspire_dls:crs=\"${xmlEscape(query.crs)}\"")
                }
                append(" language=\"${xmlEscape(query.language)}\"")
                append(" title=\"${xmlEscape(query.title)}\"")
                append(" count=\"${query.count ?: 1}\"")
                append("/>\n")
            }

            for (lang in description.languages) {
                append("<Language>$lang</Language>\n")
            }
            append("</OpenSearchDescription>\n")
        }

    private fun renderLink(
        link: AtomLink,
        type: String? = link.type,
    ): String =
        buildString {
            append("<link href=\"${xmlEscape(link.href)}\"")
            link.rel?.let { append(" rel=\"$it\"") }
            type?.let { append(" type=\"${xmlEscape(it)}\"") }
            link.length?.let { append(" length=\"$it\"") }
            link.hrefLang?.let { append(" hreflang=\"${xmlEscape(it)}\"") }
            link.title?.let { append(" title=\"${xmlEscape(it)}\"") }
            append("/>\n")
        }

    private fun renderUrlTemplate(url: de.ingrid.ingridapi.atom.model.OpensearchDescriptionUrl): String =
        "<Url type=\"${xmlEscape(url.type)}\" rel=\"${xmlEscape(url.rel)}\" template=\"${xmlEscape(url.template)}\"/>\n"

    private fun renderCategory(category: AtomCategory): String =
        "<category term=\"${xmlEscape(category.term)}\" label=\"${xmlEscape(category.label)}\"/>\n"

    private fun renderAuthor(author: de.ingrid.ingridapi.atom.model.AtomAuthor?): String =
        buildString {
            append("<author>\n")
            append("<name>${xmlEscape(author?.name)}</name>\n")
            append("<email>${xmlEscape(author?.email)}</email>\n")
            append("</author>\n")
        }
}
