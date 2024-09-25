package de.ingrid.ingridapi.portal.services

import de.ingrid.ingridapi.core.services.SearchResult
import de.ingrid.ingridapi.portal.model.Catalog
import de.ingrid.ingridapi.portal.model.CatalogsResult
import de.ingrid.ingridapi.portal.model.HitSource
import de.ingrid.ingridapi.portal.model.JsonResponse
import de.ingrid.ingridapi.portal.model.ResponseHierarchy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CatalogService {
    fun convertCatalogsResponse(response: SearchResult): CatalogsResult {
        val json = Json { ignoreUnknownKeys = true }
        val result =
            response
                .getAggregationBuckets("catalogs")
                ?.mapNotNull { convertToCatalog(json.decodeFromJsonElement<JsonResponse>(it)) } ?: emptyList()

        return CatalogsResult(result.size.toLong(), result)
    }

    private fun convertToCatalog(json: JsonResponse): Catalog {
        val id = json.key
        val source = json.getSource()
        val name = source?.dataSourceName ?: "???"
        val partner = source?.getPartner() ?: emptyList()
        val isAddress = source?.getDatatype()?.any { it == "address" } ?: false
        val isMetadata = source?.getDatatype()?.any { it == "dsc_ecs" || it == "dsc_ecs_address" } ?: false
        return Catalog(id, name, partner, isAddress, isMetadata)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun convertCatalogHierarchyResponse(response: SearchResult): List<ResponseHierarchy> {
        val json = Json { ignoreUnknownKeys = true }
        return response.hits.map {
            val hit = json.decodeFromJsonElement<HitSource>(it.jsonObject["_source"]!!)
            ResponseHierarchy(
                it.jsonObject["_id"]?.jsonPrimitive?.content ?: "?",
                hit.title,
                hit.docType ?: "?",
                hit.isFolder ?: false,
                hit.getDatatype().any { it == "address" },
            )
        }
    }
}
