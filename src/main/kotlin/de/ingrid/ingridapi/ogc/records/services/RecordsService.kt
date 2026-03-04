package de.ingrid.ingridapi.ogc.records.services

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.ElasticsearchService
import kotlinx.serialization.json.JsonObject

class RecordsService(
    val esService: ElasticsearchService,
) {
    suspend fun getCollections(): List<JsonObject> = esService.getActiveCatalogs()

    suspend fun getRecords(
        id: String,
        limit: Int = 10,
        offset: Int = 0,
    ): SearchResponse? = esService.getIndexDocuments(id, limit, offset)

    suspend fun getRecord(
        id: String,
        recordId: String,
    ): JsonObject? = esService.getIndexDocument(id, recordId)
}
