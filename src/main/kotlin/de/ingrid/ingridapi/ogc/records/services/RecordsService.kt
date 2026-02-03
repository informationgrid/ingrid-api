package de.ingrid.ingridapi.ogc.records.services

import de.ingrid.ingridapi.core.services.ElasticsearchService
import kotlinx.serialization.json.JsonObject

class RecordsService(
    val esService: ElasticsearchService,
) {
    suspend fun getCollections(): List<JsonObject> = esService.getActiveCatalogs()

    fun getRecords() {
    }

    fun getRecord() {
    }
}
