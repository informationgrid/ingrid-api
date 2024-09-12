package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging.logger

class ElasticsearchService(host: String, port: Int, https: Boolean, user: String?, password: String?) {

    private val log = logger {}
    private val client = SearchClient(KtorRestClient(host = host, port = port, https = https, user = user, password = password))

    init {
        log.info("Elastic Host: $host:$port")
    }

    suspend fun search(rawQuery: String): SearchResult {
        val indices =
            getActiveIndices().joinToString(",").also { log.debug { "Searching in indices: $it" } }
        val response = client.search(indices, rawJson = rawQuery)

        return SearchResult(
            response.total,
            response.hits?.hits ?: emptyList(),
            response.aggregations
        )
    }

    // TODO: add caching to this function
    private suspend fun getActiveIndices(): List<String> {
        return client
            .search("ingrid_meta") { query = term("active", "true") }
            .parseHits<JsonObject>()
            .mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }
    }
}

@Serializable
data class SearchResult(
    val totalHits: Long,
    val hits: List<SearchResponse.Hit>,
    val aggregations: JsonObject? = null
)
