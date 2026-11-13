package de.ingrid.ingridapi.atom.model

/**
 * Object model of the ATOM Download Service.
 *
 * This is the migration of the `de.ingrid.iface.atomDownloadService.om` classes of
 * ingrid-interface-search.
 */

/** An Atom link element. */
data class AtomLink(
    val href: String,
    val rel: String? = null,
    val type: String? = null,
    val title: String? = null,
    val hrefLang: String? = null,
    val length: Long? = null,
)

/** An Atom author element. */
data class AtomAuthor(
    val name: String? = null,
    val email: String? = null,
)

/** An Atom category element, used for the CRSs of a dataset. */
data class AtomCategory(
    val term: String? = null,
    val label: String? = null,
)

/** The Atom feed of a single download service. */
data class ServiceFeed(
    val title: String? = null,
    val subTitle: String? = null,
    val uuid: String? = null,
    val metadataAccessUrl: AtomLink? = null,
    val selfReferencingLink: AtomLink? = null,
    val openSearchDefinitionLink: AtomLink? = null,
    val identifier: String? = null,
    val copyright: String? = null,
    val updated: String? = null,
    val author: AtomAuthor? = null,
    val entries: List<ServiceFeedEntry> = emptyList(),
)

/** One entry (a coupled dataset) of a service feed. */
data class ServiceFeedEntry(
    val uuid: String? = null,
    val title: String? = null,
    val spatialDatasetIdentifierCode: String? = null,
    val spatialDatasetIdentifierNamespace: String? = null,
    val datasetMetadataRecord: AtomLink? = null,
    val datasetFeed: AtomLink? = null,
    val datasetIdentifier: String? = null,
    val rights: String? = null,
    val updated: String? = null,
    val summary: String? = null,
    val polygon: List<Double> = emptyList(),
    val crs: List<AtomCategory> = emptyList(),
    val author: AtomAuthor? = null,
)

/** The Atom feed listing all download services. */
data class ServiceFeedList(
    val selfReferencingLink: AtomLink? = null,
    val entries: List<ServiceFeed> = emptyList(),
)

/** The Atom "Dataset Feed" describing the downloadable files of one dataset. */
data class DatasetFeed(
    val title: String? = null,
    val subTitle: String? = null,
    val uuid: String? = null,
    val describedBy: List<AtomLink> = emptyList(),
    val selfReferencingLink: AtomLink? = null,
    val downloadServiceFeed: AtomLink? = null,
    val detailLink: AtomLink? = null,
    val identifier: String? = null,
    val rights: String? = null,
    val updated: String? = null,
    val author: AtomAuthor? = null,
    val entries: List<DatasetFeedEntry> = emptyList(),
)

/** One entry (a downloadable file) of a dataset feed. */
data class DatasetFeedEntry(
    val title: String? = null,
    val content: String? = null,
    val links: List<AtomLink> = emptyList(),
    val id: String? = null,
    val updated: String? = null,
    val crs: List<AtomCategory> = emptyList(),
)

/** The OpenSearch Description Document of a download service. */
data class OpensearchDescription(
    val shortName: String? = null,
    val description: String? = null,
    val selfReferencingUrlTemplate: OpensearchDescriptionUrl? = null,
    val resultsUrlTemplate: OpensearchDescriptionUrl? = null,
    val describeSpatialDatasetOperationUrlTemplate: OpensearchDescriptionUrl? = null,
    val getSpatialDatasetOperationUrlTemplate: OpensearchDescriptionUrl? = null,
    val contact: String? = null,
    val tags: String? = null,
    val longName: String? = null,
    val image: OpensearchImage? = null,
    val examples: List<OpensearchQuery> = emptyList(),
    val developer: String? = null,
    val languages: List<String> = emptyList(),
)

/** One URL template of an OpenSearch Description Document. */
data class OpensearchDescriptionUrl(
    val type: String? = null,
    val rel: String? = null,
    val template: String? = null,
)

/** An example query element of an OpenSearch Description Document. */
data class OpensearchQuery(
    val role: String? = null,
    val spatialDatasetIdentifierNamespace: String? = null,
    val spatialDatasetIdentifierCode: String? = null,
    val crs: String? = null,
    val language: String? = null,
    val title: String? = null,
    val count: Int? = null,
)

/** An image element of an OpenSearch Description Document (currently not rendered). */
data class OpensearchImage(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val type: String? = null,
)
