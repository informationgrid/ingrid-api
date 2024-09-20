package de.ingrid.ingridapi.portal.services

import de.ingrid.ingridapi.core.services.SearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CatalogsResult(
    val totalHits: Long,
    val catalogs: List<Catalog>,
)

@Serializable
data class Catalog(
    val id: String,
    val name: String,
    val partner: List<String>,
    val isAddress: Boolean,
)

class CatalogService {
    fun convertCatalogsResponse(response: SearchResult): CatalogsResult {
        val result =
            getItems(response)
                ?.mapNotNull { convertToCatalog(it.jsonObject) } ?: emptyList()

        return CatalogsResult(result.size.toLong(), result)
    }

    private fun convertToCatalog(json: JsonObject): Catalog {
        val id = json["key"]?.jsonPrimitive?.content!!
        val info =
            json["info"]
                ?.jsonObject
                ?.get("hits")
                ?.jsonObject
                ?.get("hits")
                ?.jsonArray
                ?.get(0)
                ?.jsonObject
                ?.get("_source")
        val name =
            info
                ?.jsonObject
                ?.get("dataSourceName")
                ?.jsonPrimitive
                ?.content ?: "???"
        val partner =
            kotlin
                .runCatching {
                    info
                        ?.jsonObject
                        ?.get("partner")
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                }.getOrDefault(emptyList())
        val isAddress =
            kotlin
                .runCatching {
                    info
                        ?.jsonObject
                        ?.get("datatype")
                        ?.jsonArray
                        ?.any { it.jsonPrimitive.content == "address" } ?: false
                }.getOrDefault(false)
        return Catalog(id, name, partner, isAddress)
    }

    private fun getItems(response: SearchResult) =
        response.aggregations
            ?.get("catalogs")
            ?.jsonObject
            ?.get("buckets")
            ?.jsonArray
}
