package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.JsonObject

enum class ItemExportFormat(
    val paramValue: String,
    val mediaType: String,
) {
    HTML("html", "text/html"),
    ISO("iso", "application/xml"),
    JSON("json", "application/json"),
    INDEX("index", "application/json"),
    GEOJSON("geojson", "application/geo+json"),
    INGRID_INDEX_JSON("ingrid-index-json", "application/vnd.ingrid.index+json"),
    GEODCAT_XML("geodcat", "application/rdf+xml"),
}

val SUPPORTED_ITEM_FORMATS: List<String> = ItemExportFormat.entries.map { it.paramValue }

sealed class ItemExportFormatResult {
    data class Ok(
        val format: ItemExportFormat,
    ) : ItemExportFormatResult()

    /** The `f` query parameter was provided but unknown. */
    data class InvalidParam(
        val value: String,
    ) : ItemExportFormatResult()

    /** No `f` was given and the Accept header is not satisfiable. */
    data class NotAcceptable(
        val acceptHeader: String,
    ) : ItemExportFormatResult()
}

fun parseItemExportFormat(
    param: String?,
    acceptHeader: String? = null,
): ItemExportFormat =
    when (val r = parseItemExportFormatResult(param, acceptHeader)) {
        is ItemExportFormatResult.Ok -> r.format
        else -> ItemExportFormat.HTML
    }
   /* when {
        param?.lowercase() == "html" -> ItemExportFormat.HTML
        param?.lowercase() == "iso" || param?.lowercase() == "xml" -> ItemExportFormat.ISO
        param?.lowercase() == "index" -> ItemExportFormat.INDEX
        param?.lowercase() == "json" || param?.lowercase() == "geojson" -> ItemExportFormat.GEOJSON
        param?.lowercase() == "ingrid-index-json" -> ItemExportFormat.INGRID_INDEX_JSON
        param?.lowercase() == "geodcat-xml" -> ItemExportFormat.GEODCAT_XML
        param == null && acceptHeader?.contains("application/geo+json") == true -> ItemExportFormat.GEOJSON
        param == null && acceptHeader?.contains("application/xml") == true -> ItemExportFormat.ISO
        param == null && acceptHeader?.contains("application/vnd.ingrid.index+json") == true -> ItemExportFormat.INGRID_INDEX_JSON
        param == null && acceptHeader?.contains("application/rdf+xml") == true -> ItemExportFormat.GEODCAT_XML
        param == null && acceptHeader?.contains("application/json") == true -> ItemExportFormat.INDEX
        param == null && acceptHeader?.contains("text/html") == true -> ItemExportFormat.HTML
        else -> ItemExportFormat.HTML
    }*/

fun parseItemExportFormatResult(
    param: String?,
    acceptHeader: String? = null,
): ItemExportFormatResult {
    if (param != null) {
        val match = ItemExportFormat.entries.firstOrNull { it.paramValue.equals(param, ignoreCase = true) }
        return if (match != null) ItemExportFormatResult.Ok(match) else ItemExportFormatResult.InvalidParam(param)
    }
    if (acceptHeader.isNullOrBlank()) {
        // Default for items is INDEX (Elasticsearch JSON) for compatibility.
        return ItemExportFormatResult.Ok(ItemExportFormat.INDEX)
    }
    val lower = acceptHeader.lowercase()
    ItemExportFormat.entries.firstOrNull { lower.contains(it.mediaType) }?.let { return ItemExportFormatResult.Ok(it) }
    // application/json is treated as a synonym for the INGRID index JSON
    if (lower.contains("application/json")) return ItemExportFormatResult.Ok(ItemExportFormat.INDEX)
    // Browser-style wildcards fall back to HTML
    if (lower.contains("*/*")) return ItemExportFormatResult.Ok(ItemExportFormat.HTML)
    return ItemExportFormatResult.NotAcceptable(acceptHeader)
}

interface ItemsExporter {
    suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String? = null,
    )

    suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    )
}
