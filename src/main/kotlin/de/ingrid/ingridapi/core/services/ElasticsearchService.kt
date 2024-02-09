package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import io.ktor.util.logging.*
import kotlinx.serialization.Serializable

@Serializable
data class TestDocument(
    val title: String,
)


class ElasticsearchService(host: String, port: Int) {

    private val log = KtorSimpleLogger("de.ingrid.ingridapi.core.services.ElasticsearchService")
    private val client = SearchClient(
        KtorRestClient(host = host, port = port)
    )

    init {
        log.info("Elastic Host: $host:$port")
    }
    
    suspend fun search(rawQuery: String): SearchResponse.Hits? {
        return client.search("ingrid", rawJson = rawQuery ).hits
    }

    suspend fun search(searchQuery: SearchDSL? = null): SearchResponse.Hits? {
        val results = if (searchQuery == null) client.search("")
        else client.search("", searchQuery)

        log.debug("found ${results.total} hits")
        results
            // extension function that deserializes
            // the hits using kotlinx.serialization
            .parseHits<TestDocument>()
            .forEach {
                // we feel lucky
                log.debug("doc: ${it.title}")
            }
        // you can also get the JsonObject if you don't
        // have a model class
        return results.hits
    }
}