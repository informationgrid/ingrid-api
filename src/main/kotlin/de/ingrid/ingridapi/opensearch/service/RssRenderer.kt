package de.ingrid.ingridapi.opensearch.service

import de.ingrid.ingridapi.core.services.asSafeString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Escapes a value for the use inside XML text and attribute values. */
internal fun xmlEscape(value: String?): String =
    value
        .orEmpty()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

/**
 * Renders the search result as an RSS 2.0 document with OpenSearch 1.1 response elements and
 * the InGrid extension namespace.
 *
 * This is the migration of the response rendering of `de.ingrid.iface.opensearch.OpensearchServlet`.
 */
object RssRenderer {

    /**
     * Renders the RSS response for the given search result.
     *
     * @param params the request parameters (used for the channel title and the echoed query)
     * @param result the search result
     * @param baseUrl the absolute base URL of the application (scheme, host, optional port,
     *   context path), used to build absolute links
     * @param selfUrl the absolute URL of the current request, used as channel link and item
     *   link fallback
     * @param channelDescription the default channel description
     * @param detailsUrlTemplate optional URL template with a `{uuid}` placeholder used to build
     *   the links of the items
     */
    fun render(
        params: OpenSearchParams,
        result: OpenSearchResult,
        baseUrl: String,
        selfUrl: String,
        channelDescription: String,
        detailsUrlTemplate: String,
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append(
            """<rss xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/""""
                + """ xmlns:relevance="http://a9.com/-/opensearch/extensions/relevance/1.0/""""
                + """ xmlns:ingrid="http://www.portalu.de/opensearch/extension/1.0" version="2.0">""",
        )
        sb.append("<channel>")
        sb.append("<title>${xmlEscape(channelTitle(params))}</title>")
        sb.append("<link>${xmlEscape(selfUrl)}</link>")
        sb.append("<description>${xmlEscape(channelDescription)}</description>")
        sb.append("<opensearch:totalResults>${result.totalResults}</opensearch:totalResults>")
        sb.append("<opensearch:startIndex>${(params.page - 1) * params.hitsPerPage + 1}</opensearch:startIndex>")
        sb.append("<opensearch:itemsPerPage>${params.hitsPerPage}</opensearch:itemsPerPage>")
        sb.append("""<opensearch:Query role="request" searchTerms="${xmlEscape(params.query)}"/>""")

        for (hit in result.hits) {
            renderItem(sb, result, hit, baseUrl, selfUrl, detailsUrlTemplate)
        }

        sb.append("</channel></rss>")
        return sb.toString()
    }

    private fun renderItem(
        sb: StringBuilder,
        result: OpenSearchResult,
        hit: OpenSearchHit,
        baseUrl: String,
        selfUrl: String,
        detailsUrlTemplate: String,
    ) {
        sb.append("<item>")
        sb.append("<title>${xmlEscape(itemTitle(hit))}</title>")
        sb.append("<link>${xmlEscape(itemLink(hit, result, baseUrl, selfUrl, detailsUrlTemplate))}</link>")
        sb.append("<description>${xmlEscape(itemDescription(hit))}</description>")
        hit.score?.let { sb.append("<relevance:score>$it</relevance:score>") }
        sb.append("<ingrid:docuuid>${xmlEscape(hit.id)}</ingrid:docuuid>")
        val dataSource = result.dataSourceByIndex[hit.index] ?: hit.index
        sb.append("<ingrid:source>${xmlEscape(dataSource)}</ingrid:source>")
        val modified = itemLastModified(hit)
        if (modified.isNotEmpty()) {
            sb.append("<ingrid:last-modified>${xmlEscape(modified)}</ingrid:last-modified>")
        }
        sb.append("</item>")
    }

    private fun channelTitle(params: OpenSearchParams): String =
        params.channelTitle?.takeIf { it.isNotEmpty() }
            ?: "ingrid OpenSearch: ${params.query.ifEmpty { "empty query" }}"

    private fun itemTitle(hit: OpenSearchHit): String = hit.source["title"].asSafeString().ifEmpty { hit.id }

    private fun itemDescription(hit: OpenSearchHit): String =
        (hit.source["description"] ?: hit.source["summary"]).asSafeString()

    /**
     * Renders the last-modified timestamp of the document as ISO 8601 date-time. Numeric
     * values are interpreted as epoch milliseconds (like the `t01_object.mod_time` values of
     * the InGrid index documents), formatted in the Europe/Berlin time zone as in the legacy
     * OpenSearch interface.
     */
    private fun itemLastModified(hit: OpenSearchHit): String =
        when (val value = hit.source["modified"] ?: hit.source["t01_object.mod_time"]) {
            null -> ""
            is JsonPrimitive ->
                if (value.isString) {
                    value.content
                } else {
                    value.doubleOrNull?.let { formatEpochMillis(it.toLong()) } ?: value.content
                }

            else -> value.asSafeString()
        }

    private fun formatEpochMillis(millis: Long): String =
        ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("Europe/Berlin"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * Builds the link of an item: the configured details URL (with the `{uuid}` placeholder
     * replaced), or a link to the record in the OGC API - Records interface, or as last resort
     * the URL of the current request.
     */
    private fun itemLink(
        hit: OpenSearchHit,
        result: OpenSearchResult,
        baseUrl: String,
        selfUrl: String,
        detailsUrlTemplate: String,
    ): String {
        if (detailsUrlTemplate.isNotEmpty()) {
            return detailsUrlTemplate.replace("{uuid}", hit.id)
        }
        val dataSource = result.dataSourceByIndex[hit.index]
        if (dataSource != null) {
            return "${baseUrl.trimEnd('/')}/ogc/records/collections/$dataSource/items/${hit.id}?f=html"
        }
        return selfUrl
    }
}
