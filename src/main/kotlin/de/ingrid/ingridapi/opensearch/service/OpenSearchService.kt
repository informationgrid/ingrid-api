package de.ingrid.ingridapi.opensearch.service

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.SearchResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The request parameters of the OpenSearch query endpoint. */
data class OpenSearchParams(
    /** The InGrid query string from the `q` parameter. */
    val query: String,
    /** The requested result page, starting at 1. */
    val page: Int,
    /** The number of hits per result page. */
    val hitsPerPage: Int,
    /** Optional channel title from the `title` parameter. */
    val channelTitle: String? = null,
    /** Whether the UVP-specific response format is requested (legacy parameter `uvp=1`). */
    val withUvp: Boolean = false,
)

/** One hit of an OpenSearch search result. */
data class OpenSearchHit(
    /** The unique id of the document (`_id`). */
    val id: String,
    /** The index the document was found in (`_index`). */
    val index: String,
    /** The relevance score of the hit (`_score`), may be `null`. */
    val score: Double?,
    /** The source document (`_source`). */
    val source: JsonObject,
)

/** The result of an OpenSearch search. */
data class OpenSearchResult(
    /** The total number of hits for the query. */
    val totalResults: Long,
    /** The hits of the requested page. */
    val hits: List<OpenSearchHit>,
    /** Maps an Elasticsearch index name to the name of its data source (catalog). */
    val dataSourceByIndex: Map<String, String>,
)

/**
 * Executes OpenSearch queries against the Elasticsearch indices of the active catalogs.
 *
 * This is the migration of `de.ingrid.iface.opensearch.OpensearchServlet`, with the iBus
 * search replaced by a direct Elasticsearch search.
 */
class OpenSearchService(
    private val esService: ElasticsearchService,
) {
    /**
     * Translates the InGrid query string into an Elasticsearch query, executes it against all
     * active indices and maps the hits for the RSS rendering.
     */
    suspend fun search(params: OpenSearchParams): OpenSearchResult {
        val parsedQuery = IngridQueryParser.parse(params.query)
        val from = (params.page - 1) * params.hitsPerPage
        val esQuery = EsQueryBuilder.build(parsedQuery, from, params.hitsPerPage)
        val searchResult: SearchResult = esService.search(esQuery.toString())

        val dataSourceByIndex =
            esService
                .getActiveCatalogs()
                .mapNotNull { catalog ->
                    val index = catalog["linkedIndex"]?.jsonPrimitive?.content
                    val dataSource =
                        catalog["plugdescription"]
                            ?.jsonObject
                            ?.get("dataSourceName")
                            ?.jsonPrimitive
                            ?.content
                    if (index != null && dataSource != null) index to dataSource else null
                }.toMap()

        val hits =
            searchResult.hits.mapNotNull { element ->
                val hit = element as? JsonObject ?: return@mapNotNull null
                OpenSearchHit(
                    id = hit["_id"]?.jsonPrimitive?.content ?: "",
                    index = hit["_index"]?.jsonPrimitive?.content ?: "",
                    score = hit["_score"]?.jsonPrimitive?.doubleOrNull,
                    source = hit["_source"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }

        return OpenSearchResult(searchResult.totalHits, hits, dataSourceByIndex)
    }
}
