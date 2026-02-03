package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.parseHits
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.total
import com.jillesvangurp.searchdsls.querydsl.term
import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        log.info { "Elastic Host: ${config.elasticHost}:${config.elasticPort}" }
    }

    suspend fun search(rawQuery: String): SearchResult {
        val indices =
            getActiveIndices().joinToString(",").also { log.debug { "Searching in indices: $it" } }
        val response = client.search(indices, rawJson = rawQuery, ignoreUnavailable = true)

        log.debug { "Found ${response.hits?.hits?.size} hits on indices: $indices" }
        return SearchResult(
            response.total,
            Json.encodeToJsonElement(response.hits?.hits ?: emptyList()) as JsonArray,
            response.aggregations,
        )
    }

    suspend fun getActiveCatalogs(): List<JsonObject> =
        client
            .search("ingrid_meta") {
                query = term("active", "true")
                from = 0
                resultSize = 100
            }.parseHits<JsonObject>()

    // TODO: add caching to this function
    private suspend fun getActiveIndices(): List<String> =
        getActiveCatalogs()
            .mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }
}

@Serializable
data class SearchResult(
    val totalHits: Long,
    val hits: JsonArray,
    val aggregations: JsonObject? = null,
) {
    fun getAggregationBuckets(agg: String): JsonArray? =
        aggregations
            ?.get(agg)
            ?.jsonObject
            ?.get("buckets")
            ?.jsonArray
}

// @Serializable
// data class SearchResultHits
