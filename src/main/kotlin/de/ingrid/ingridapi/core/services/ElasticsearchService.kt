package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.parseHits
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.total
import com.jillesvangurp.searchdsls.querydsl.term
import de.ingrid.ingridapi.config.AppConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging.logger

class ElasticsearchService(
    config: AppConfig,
) {
    private val log = logger {}
    private val client =
        SearchClient(
            KtorRestClient(
                host = config.elasticHost,
                port = config.elasticPort,
                https = config.elasticHttps,
                user = config.elasticUsername,
                password = config.elasticPassword,
            ),
        )

    init {
        log.info("Elastic Host: ${config.elasticHost}:${config.elasticPort}")
    }

    suspend fun search(rawQuery: String): SearchResult {
        val indices =
            getActiveIndices().joinToString(",").also { log.debug { "Searching in indices: $it" } }
        val response = client.search(indices, rawJson = rawQuery)

        return SearchResult(
            response.total,
            response.hits?.hits ?: emptyList(),
            response.aggregations,
        )
    }

    // TODO: add caching to this function
    private suspend fun getActiveIndices(): List<String> =
        client
            .search("ingrid_meta") { query = term("active", "true") }
            .parseHits<JsonObject>()
            .mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }
}

@Serializable
data class SearchResult(
    val totalHits: Long,
    val hits: List<SearchResponse.Hit>,
    val aggregations: JsonObject? = null,
)
