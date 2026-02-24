package de.ingrid.ingridapi.ogc.records.services

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.ElasticsearchService
import kotlinx.serialization.json.JsonObject

class RecordsService(
    val esService: ElasticsearchService,
) {
    suspend fun getCollections(): List<JsonObject> = esService.getActiveCatalogs()

    suspend fun getRecords(id: String): SearchResponse.Hits? = esService.getIndexDocuments(id, 10, 0)

    fun getRecord() {
    }
}
