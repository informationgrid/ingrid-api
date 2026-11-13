package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.atom.model.AtomLink
import io.ktor.server.application.ApplicationCall
import java.net.URLEncoder

/** The request of the service feed endpoint (`/atom/service/{uuid}`). */
data class ServiceFeedRequest(
    /** The uuid of the download service, taken from the path. */
    val uuid: String,
    /** The optional InGrid query string from the `q` parameter, used to filter the entries. */
    val query: String? = null,
) {
    companion object {
        fun fromCall(call: ApplicationCall): ServiceFeedRequest =
            ServiceFeedRequest(
                uuid = call.parameters["uuid"].orEmpty(),
                query = call.request.queryParameters["q"],
            )
    }
}

/** The request of the service feed list endpoint (`/atom/service-list`). */
data class ServiceFeedListRequest(
    /** The optional InGrid query string from the `q` parameter. */
    val query: String? = null,
) {
    companion object {
        fun fromCall(call: ApplicationCall): ServiceFeedListRequest =
            ServiceFeedListRequest(
                query = call.request.queryParameters["q"],
            )
    }
}

/**
 * The request of the dataset feed and get-dataset endpoints (`/atom/dataset/{uuid}` and
 * `/atom/get-dataset/{uuid}`).
 */
data class DatasetFeedRequest(
    /** The uuid of the download service, taken from the path. */
    val serviceFeedUuid: String,
    /**
     * The uuid of the dataset, taken from the `datasetUuid` parameter. May also be an (external)
     * CSW GetRecordById URL for coupled resources of external catalogs.
     */
    val datasetFeedUuid: String? = null,
    /** The code part of the INSPIRE spatial dataset identifier (`spatial_dataset_identifier_code`). */
    val spatialDatasetIdentifierCode: String? = null,
    /** The namespace part of the INSPIRE spatial dataset identifier (`spatial_dataset_identifier_namespace`). */
    val spatialDatasetIdentifierNamespace: String? = null,
    /** The requested language of the dataset (`language`). */
    val language: String? = null,
    /** The requested coordinate reference system (`crs`). */
    val crs: String? = null,
    /** Whether a link to the portal detail page shall be added (`detail`). */
    val detail: Boolean = false,
) {
    companion object {
        fun fromCall(call: ApplicationCall): DatasetFeedRequest =
            DatasetFeedRequest(
                serviceFeedUuid = call.parameters["uuid"].orEmpty(),
                datasetFeedUuid = call.request.queryParameters["datasetUuid"],
                spatialDatasetIdentifierCode = call.request.queryParameters["spatial_dataset_identifier_code"],
                spatialDatasetIdentifierNamespace = call.request.queryParameters["spatial_dataset_identifier_namespace"],
                language = call.request.queryParameters["language"],
                crs = call.request.queryParameters["crs"],
                detail = call.request.queryParameters["detail"] == "true",
            )
    }
}

/** The request of the OpenSearch description endpoint (`/atom/opensearch-description/{uuid}`). */
data class OpensearchDescriptionRequest(
    /** The uuid of the download service, taken from the path. */
    val uuid: String,
) {
    companion object {
        fun fromCall(call: ApplicationCall): OpensearchDescriptionRequest =
            OpensearchDescriptionRequest(
                uuid = call.parameters["uuid"].orEmpty(),
            )
    }
}

/**
 * Builds the absolute URLs of the ATOM Download Service endpoints for links inside the feeds.
 *
 * The legacy interface used configured URL patterns (`atom.download.service.url` and the
 * `atom.download.*.extension` properties); here the URLs are derived from the base URL of the
 * current request instead, so they are always consistent with the deployment (context path,
 * proxy, port).
 */
class AtomUrlBuilder(private val baseUrl: String) {

    private val base = baseUrl.trimEnd('/')

    /** The URL of the service feed of the given download service. */
    fun serviceFeed(serviceFeedUuid: String): String = "$base/atom/service/${encodeForPath(serviceFeedUuid)}"

    /** The URL of the service feed list, optionally filtered by an InGrid query string. */
    fun serviceFeedList(query: String?): String =
        if (query.isNullOrEmpty()) "$base/atom/service-list" else "$base/atom/service-list?q=${urlEncode(query)}"

    /** The URL of the dataset feed of the given dataset within the given service. */
    fun datasetFeed(
        serviceFeedUuid: String,
        datasetFeedUuid: String,
    ): String = "$base/atom/dataset/${encodeForPath(serviceFeedUuid)}?datasetUuid=${urlEncode(datasetFeedUuid)}"

    /** The URL of the get-dataset endpoint of the given dataset within the given service. */
    fun getDataset(
        serviceFeedUuid: String,
        spatialDatasetIdentifierCode: String?,
        spatialDatasetIdentifierNamespace: String?,
        crs: String?,
        language: String?,
    ): String {
        val params =
            buildList {
                if (!spatialDatasetIdentifierCode.isNullOrEmpty()) add("spatial_dataset_identifier_code=${urlEncode(spatialDatasetIdentifierCode)}")
                if (!spatialDatasetIdentifierNamespace.isNullOrEmpty()) add("spatial_dataset_identifier_namespace=${urlEncode(spatialDatasetIdentifierNamespace)}")
                if (!crs.isNullOrEmpty()) add("crs=${urlEncode(crs)}")
                if (!language.isNullOrEmpty()) add("language=${urlEncode(language)}")
            }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "$base/atom/get-dataset/${encodeForPath(serviceFeedUuid)}$query"
    }

    /** The URL of the OpenSearch Description Document of the given download service. */
    fun opensearchDescription(serviceFeedUuid: String): String =
        "$base/atom/opensearch-description/${encodeForPath(serviceFeedUuid)}"

    /** The generic results URL template for browser integration (`rel="results"`, `type="text/html"`). */
    fun serviceFeedWithSearchTerms(serviceFeedUuid: String): String =
        "${serviceFeed(serviceFeedUuid)}?q={searchTerms}"

    /** The "Describe Spatial Data Set Operation" request URL template of the OpenSearch description. */
    fun describeSpatialDataset(serviceFeedUuid: String): String =
        "$base/atom/dataset/${encodeForPath(serviceFeedUuid)}" +
            "?spatial_dataset_identifier_code={inspire_dls:spatial_dataset_identifier_code?}" +
            "&spatial_dataset_identifier_namespace={inspire_dls:spatial_dataset_identifier_namespace?}" +
            "&language={language?}"

    /** The "Get Spatial Data Set Operation" request URL template of the OpenSearch description. */
    fun getSpatialDataset(serviceFeedUuid: String): String =
        "$base/atom/get-dataset/${encodeForPath(serviceFeedUuid)}" +
            "?spatial_dataset_identifier_code={inspire_dls:spatial_dataset_identifier_code?}" +
            "&spatial_dataset_identifier_namespace={inspire_dls:spatial_dataset_identifier_namespace?}" +
            "&crs={inspire_dls:crs?}" +
            "&language={language?}"

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        /** Creates an Atom link pointing to a feed document. */
        fun feedLink(
            href: String,
            rel: String,
            title: String? = null,
        ): AtomLink =
            AtomLink(
                href = href,
                rel = rel,
                type = "application/atom+xml",
                hrefLang = "de",
                title = title,
            )
    }
}
