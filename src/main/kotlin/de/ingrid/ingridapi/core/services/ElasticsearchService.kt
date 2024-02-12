package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging.logger

class ElasticsearchService(host: String, port: Int) {

    private val log = logger {}
    private val client = SearchClient(KtorRestClient(host = host, port = port))

    init {
        log.info("Elastic Host: $host:$port")
    }

    suspend fun search(rawQuery: String): SearchResponse.Hits? {
        val indices =
            getActiveIndices().joinToString(",").also { log.debug { "Searching in indices: $it" } }
        return client.search(indices, rawJson = rawQuery).hits
    }

    private suspend fun getActiveIndices(): List<String> {
        return client
            .search("ingrid_meta") { query = term("active", "true") }
            .parseHits<JsonObject>()
            .mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }
    }
}
