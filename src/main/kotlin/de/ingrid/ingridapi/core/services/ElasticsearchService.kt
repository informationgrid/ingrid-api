package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import io.ktor.util.logging.*
import kotlinx.serialization.Serializable

@Serializable
data class TestDocument(
    val title: String,
)


class ElasticsearchService {


    internal val log = KtorSimpleLogger("de.ingrid.ingridapi.core.services.ElasticsearchService")
    private val client = SearchClient(
        KtorRestClient(host = "localhost", port = 9200)
    )

    suspend fun search(searchQuery: SearchDSL? = null): SearchResponse.Hits? {
        val results = if (searchQuery == null) client.search("ingrid_cat")
        else client.search("ingrid_cat", searchQuery)

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