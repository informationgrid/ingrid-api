package de.ingrid.ingridapi.core.services

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.count
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.getAliases
import com.jillesvangurp.ktsearch.parseHit
import com.jillesvangurp.ktsearch.parseHits
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.searchHits
import com.jillesvangurp.ktsearch.total
import com.jillesvangurp.ktsearch.updateDocument
import com.jillesvangurp.searchdsls.querydsl.term
import de.ingrid.ingridapi.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SearchException(
    message: String,
) : Exception(message)

class ElasticsearchService(
    config: AppConfig,
) {
    private val log = logger {}
    private val client =
        SearchClient(
            KtorRestClient(
                host = config.elasticHost,
                port = config.elasticPort,
                https = config.elasticHttps,
                user = config.elasticUsername,
                password = config.elasticPassword,
            ),
        )

    init {
        log.info { "Elastic Host: ${config.elasticHost}:${config.elasticPort}" }
    }

    suspend fun search(rawQuery: String): SearchResult {
        val indices =
            getActiveIndices().joinToString(",").also { log.debug { "Searching in indices: $it" } }
        if (indices.isEmpty()) return SearchResult(0, JsonArray(emptyList()))

        try {
            val response = client.search(indices, rawJson = rawQuery, ignoreUnavailable = true)

            log.debug { "Found ${response.hits?.hits?.size} hits on indices: $indices" }
            return SearchResult(
                response.total,
                Json.encodeToJsonElement(response.hits?.hits ?: emptyList()) as JsonArray,
                response.aggregations,
            )
        } catch (ex: Exception) {
            val message = ex.message
            val reasonString = "\"reason\":"
            val reason =
                if (message != null && message.contains(reasonString)) {
                    message
                        .substringAfter(reasonString)
                        .substringBefore(",")
                        .trim('"', ' ')
                } else {
                    message
                }
            log.error { "Error while searching in indices: $reason" }
            log.debug(ex) { "Full error details" }
            throw SearchException("Error while searching in indices: $reason")
        }
    }

    suspend fun getActiveCatalogs(): List<JsonObject> =
        client
            .search("ingrid_meta") {
                query = term("active", "true")
                from = 0
                resultSize = 100
            }.parseHits<JsonObject>()

    suspend fun getIndexDocuments(
        id: String,
        size: Int,
        from: Int,
        bbox: List<Double>? = null,
    ): SearchResponse? {
        val filteredCatalogs =
            getActiveCatalogs()
                .filter {
                    it["plugdescription"]
                        ?.jsonObject["dataSourceName"]
                        ?.jsonPrimitive
                        ?.content
                        .equals(id, ignoreCase = true)
                }.mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }

        if (filteredCatalogs.isEmpty()) {
            return null
        }
        val indices =
            filteredCatalogs.joinToString(",").also { log.debug { "Searching in indices: $it" } }

        val queryJson =
            if (bbox != null && bbox.size >= 4) {
                buildJsonObject {
                    put(
                        "query",
                        buildJsonObject {
                            put(
                                "bool",
                                buildJsonObject {
                                    put(
                                        "must",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("match_all", buildJsonObject { })
                                                },
                                            )
                                        },
                                    )
                                    put(
                                        "filter",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put(
                                                        "geo_shape",
                                                        buildJsonObject {
                                                            put(
                                                                "spatial.geometries",
                                                                buildJsonObject {
                                                                    put(
                                                                        "shape",
                                                                        buildJsonObject {
                                                                            put("type", JsonPrimitive("envelope"))
                                                                            put(
                                                                                "coordinates",
                                                                                buildJsonArray {
                                                                                    add(
                                                                                        buildJsonArray {
                                                                                            add(JsonPrimitive(bbox[0]))
                                                                                            add(JsonPrimitive(bbox[3]))
                                                                                        },
                                                                                    )
                                                                                    add(
                                                                                        buildJsonArray {
                                                                                            add(JsonPrimitive(bbox[2]))
                                                                                            add(JsonPrimitive(bbox[1]))
                                                                                        },
                                                                                    )
                                                                                },
                                                                            )
                                                                        },
                                                                    )
                                                                    put("relation", JsonPrimitive("intersects"))
                                                                },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }.toString()
            } else {
                null
            }

        return if (queryJson != null) {
            client.search(indices, rawJson = queryJson, size = size, from = from)
        } else {
            client.search(indices, size = size, from = from)
        }
    }

    suspend fun getIndexDocument(
        id: String,
        recordId: String,
    ): JsonObject? {
        val filteredCatalogs =
            getActiveCatalogs()
                .filter {
                    it["plugdescription"]
                        ?.jsonObject["dataSourceName"]
                        ?.jsonPrimitive
                        ?.content
                        .equals(id, ignoreCase = true)
                }.mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }

        if (filteredCatalogs.isEmpty()) {
            return null
        }
        val indices =
            filteredCatalogs.joinToString(",").also { log.debug { "Searching in indices: $it" } }

        return try {
            client
                .search(indices) {
                    query = term("_id", recordId)
                }.parseHits<JsonObject>()
                .firstOrNull()
        } catch (ex: Exception) {
            log.error { "Error while fetching document $recordId from indices $indices: ${ex.message}" }
            null
        }
    }

    // TODO: add caching to this function
    private suspend fun getActiveIndices(): List<String> =
        getActiveCatalogs()
            .mapNotNull { it["linkedIndex"]?.jsonPrimitive?.content }

    // ---------------------------------------------------------------------
    // Admin API: index management and ingrid_meta administration
    // ---------------------------------------------------------------------

    /** The name of the meta index that describes all managed iPlug indices. */
    val metaIndexName: String = "ingrid_meta"

    /** All indices on the cluster together with their aliases. */
    suspend fun listIndicesWithAliases(): Map<String, Set<String>> =
        client
            .getAliases()
            .mapValues { it.value.aliases.keys }

    /** Number of documents in the given index (or 0 if not available). */
    suspend fun countDocuments(index: String): Long =
        try {
            client.count(target = index).count
        } catch (ex: Exception) {
            log.warn { "Could not count documents for index '$index': ${ex.message}" }
            0L
        }

    /** Delete an index by name. */
    suspend fun deleteIndex(index: String) {
        log.info { "Deleting index '$index'" }
        client.deleteIndex(index)
    }

    /**
     * Returns the meta entries from the `ingrid_meta` index together with the document `_id`
     * so that they can be updated individually.
     */
    suspend fun getMetaEntries(): List<IngridMetaEntry> {
        val response =
            try {
                client.search(metaIndexName) {
                    from = 0
                    resultSize = 1000
                }
            } catch (ex: Exception) {
                log.warn { "Could not load '$metaIndexName' entries: ${ex.message}" }
                return emptyList()
            }
        return response.searchHits.map { hit ->
            val source = hit.parseHit<JsonObject>()
            IngridMetaEntry(
                docId = hit.id,
                indexId = source["indexId"]?.jsonPrimitive?.content,
                linkedIndex = source["linkedIndex"]?.jsonPrimitive?.content,
                active = source["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                dataSourceName =
                    source["plugdescription"]
                        ?.jsonObject
                        ?.get("dataSourceName")
                        ?.jsonPrimitive
                        ?.content,
                lastIndexed = source["lastIndexed"]?.jsonPrimitive?.content,
            )
        }
    }

    /** Set the `active` flag on an `ingrid_meta` document. */
    suspend fun setMetaActive(
        docId: String,
        active: Boolean,
    ) {
        log.info { "Setting active=$active on ingrid_meta document '$docId'" }
        client.updateDocument(
            target = metaIndexName,
            id = docId,
            docJson = """{"active": $active}""",
            refresh = Refresh.True,
        )
    }
}

/** Represents one document of the `ingrid_meta` index needed by the admin GUI. */
data class IngridMetaEntry(
    val docId: String,
    val indexId: String?,
    val linkedIndex: String?,
    val active: Boolean,
    val dataSourceName: String?,
    val lastIndexed: String? = null,
)

@Serializable
data class SearchResult(
    val totalHits: Long,
    val hits: JsonArray,
    val aggregations: JsonObject? = null,
) {
    fun getAggregationBuckets(agg: String): JsonArray? =
        aggregations
            ?.get(agg)
            ?.jsonObject
            ?.get("buckets")
            ?.jsonArray
}

// @Serializable
// data class SearchResultHits
