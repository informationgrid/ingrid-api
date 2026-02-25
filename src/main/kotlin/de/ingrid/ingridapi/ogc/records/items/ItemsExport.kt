package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject

enum class ItemExportFormat {
    HTML,
    ISO,
    INDEX,
}

fun parseItemExportFormat(param: String?): ItemExportFormat =
    when (param?.lowercase()) {
        "html" -> ItemExportFormat.HTML
        "iso" -> ItemExportFormat.ISO
        "index" -> ItemExportFormat.INDEX
        else -> ItemExportFormat.HTML
    }

interface ItemsExporter {
    suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
    )

    suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
    )
}
