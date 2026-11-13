package de.ingrid.ingridapi.atom.service

import de.ingrid.ingridapi.config.AppConfig
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.opensearch.service.EsQueryBuilder
import de.ingrid.ingridapi.opensearch.service.IngridQueryParser
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.w3c.dom.Document

/**
 * Searches the metadata records for the ATOM Download Service in the Elasticsearch indices of
 * the active catalogs and extracts the IDF documents.
 *
 * This is the migration of the iBus-based search of ingrid-interface-search
 * (`de.ingrid.iface.atomDownloadService.util.IngridQueryProducer`, `de.ingrid.iface.util.IBusHelper`
 * and `de.ingrid.iface.util.IdfUtils`), with the iBus replaced by a direct Elasticsearch search.
 * The IDF documents are read from the `idf` field of the index documents, like in the
 * ISO exporter of the OGC API - Records interface.
 *
 * The legacy InGrid query `ranking:score dataType:dsc_ecs (t01_object.obj_id:"<uuid>" OR
 * t01_object.org_obj_id:"<uuid>")` translates into a search for the document `_id` (which is the
 * object id in this index) or the `t01_object.org_obj_id` field. The legacy `ranking:score` and
 * `dataType:dsc_ecs` meta terms have no equivalent in this index and are dropped.
 */
class AtomSearchService(
    private val esService: ElasticsearchService,
    private val config: AppConfig,
) {
    private val log = logger {}

    /** Maximum number of hits fetched for feed lists and coupled-resource lookups. */
    private val maxHits = 1000

    /**
     * Finds the metadata record with the given uuid (by object id or original object id) and
     * returns its parsed IDF document, or `null` if no record was found.
     */
    suspend fun findDocumentByUuid(uuid: String): Document? {
        val query =
            buildJsonObject {
                put("from", 0)
                put("size", 10)
                put("query", uuidQuery(listOf(uuid), withQueryExtension = true))
            }
        return idfDocuments(query).firstOrNull()
    }

    /**
     * Finds all metadata records of download services (`t011_obj_serv.has_atom_download:Y`),
     * optionally restricted by an InGrid query string, and returns their parsed IDF documents.
     */
    suspend fun findDownloadServiceDocuments(query: String?): List<Document> {
        val must =
            buildList {
                add(matchQuery("t011_obj_serv.has_atom_download", "Y"))
                if (!query.isNullOrEmpty()) add(parseIngridQuery(query))
                addQueryExtensionTo(this)
            }
        val request =
            buildJsonObject {
                put("from", 0)
                put("size", maxHits)
                put("query", boolQuery(must))
            }
        return idfDocuments(request)
    }

    /**
     * Finds metadata records by their spatial dataset identifier (`t011_obj_geo.datasource_uuid`),
     * optionally restricted by a reference system (`t011_obj_geo.referencesystem_id`) and a
     * language (`lang`).
     */
    suspend fun findDatasetDocumentsBySpatialIdentifier(
        code: String?,
        namespace: String?,
        crs: String?,
        language: String?,
    ): List<Document> {
        if (code.isNullOrEmpty()) return emptyList()
        val identifiers = mutableListOf(code)
        if (!namespace.isNullOrEmpty()) identifiers.add("$namespace#$code")

        val must =
            buildList {
                add(identifiersQuery(identifiers))
                if (!crs.isNullOrEmpty()) add(matchQuery("t011_obj_geo.referencesystem_id", crs))
                if (!language.isNullOrEmpty() && config.atomSupportedLanguages.contains(language)) {
                    add(matchQuery("lang", language))
                }
                addQueryExtensionTo(this)
            }
        val request =
            buildJsonObject {
                put("from", 0)
                put("size", maxHits)
                put("query", boolQuery(must))
            }
        return idfDocuments(request)
    }

    /** Finds the metadata records for the given uuids (coupled resources of a service feed). */
    suspend fun findCoupledResourceDocuments(
        uuids: List<String>,
        query: String?,
    ): List<Document> {
        if (uuids.isEmpty()) return emptyList()
        val must =
            buildList {
                add(uuidQuery(uuids, withQueryExtension = false))
                if (!query.isNullOrEmpty()) add(parseIngridQuery(query))
                addQueryExtensionTo(this)
            }
        val request =
            buildJsonObject {
                put("from", 0)
                put("size", maxHits)
                put("query", boolQuery(must))
            }
        return idfDocuments(request)
    }

    /**
     * Builds the query for a search by uuid(s): the document `_id` (the object id of the
     * index documents) or the `t01_object.org_obj_id` field.
     */
    private fun uuidQuery(
        uuids: List<String>,
        withQueryExtension: Boolean,
    ) = buildJsonObject {
        put(
            "bool",
            buildJsonObject {
                put(
                    "should",
                    buildJsonArray {
                        add(termsQuery("_id", uuids))
                        add(termsQuery("t01_object.org_obj_id", uuids))
                    },
                )
                put("minimum_should_match", 1)
            },
        )
    }.let { base ->
        if (withQueryExtension && config.atomQueryExtension.isNotEmpty()) {
            boolQuery(listOf(base, parseIngridQuery(config.atomQueryExtension)))
        } else {
            base
        }
    }

    /** Builds the query for a search by spatial dataset identifier(s). */
    private fun identifiersQuery(identifiers: List<String>): JsonObject =
        buildJsonObject {
            put(
                "bool",
                buildJsonObject {
                    put(
                        "should",
                        buildJsonArray {
                            identifiers.forEach { identifier -> add(matchQuery("t011_obj_geo.datasource_uuid", identifier)) }
                        },
                    )
                    put("minimum_should_match", 1)
                },
            )
        }

    private fun termsQuery(
        field: String,
        values: List<String>,
    ): JsonObject =
        buildJsonObject {
            put("terms", buildJsonObject { put(field, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) })
        }

    private fun matchQuery(
        field: String,
        value: String,
    ): JsonObject =
        buildJsonObject {
            put("match", buildJsonObject { put(field, buildJsonObject { put("query", value) }) })
        }

    private fun boolQuery(must: List<JsonObject>): JsonObject =
        buildJsonObject {
            put(
                "bool",
                buildJsonObject {
                    put("must", buildJsonArray { must.forEach { add(it) } })
                },
            )
        }

    /** Appends the configured query extension (an InGrid query string) as an additional clause. */
    private fun addQueryExtensionTo(must: MutableList<JsonObject>) {
        if (config.atomQueryExtension.isNotEmpty()) {
            must.add(parseIngridQuery(config.atomQueryExtension))
        }
    }

    /**
     * Translates an InGrid query string into an Elasticsearch query, dropping the iBus meta
     * terms (`ranking:score`, `dataType:dsc_ecs`, ...) as done by the OpenSearch interface.
     */
    private fun parseIngridQuery(query: String): JsonObject =
        try {
            EsQueryBuilder.buildQuery(IngridQueryParser.parse(query)) as JsonObject
        } catch (ex: Exception) {
            log.warn { "Ignoring invalid InGrid query string '$query': ${ex.message}" }
            buildJsonObject { put("match_all", buildJsonObject { }) }
        }

    /** Executes the search and parses the `idf` field of all hits. */
    private suspend fun idfDocuments(query: JsonObject): List<Document> {
        val result = esService.search(query.toString())
        return result.hits
            .mapNotNull { element ->
                val hit = element as? JsonObject ?: return@mapNotNull null
                val source = hit["_source"] as? JsonObject ?: return@mapNotNull null
                val idf = source["idf"]?.jsonPrimitive?.content
                val id = hit["_id"]?.jsonPrimitive?.content
                if (idf.isNullOrEmpty()) {
                    log.warn { "Document without IDF content found: $id" }
                    return@mapNotNull null
                }
                try {
                    IdfXml.parse(idf)
                } catch (ex: Exception) {
                    log.error { "Error parsing IDF document $id: ${ex.message}" }
                    null
                }
            }
    }
}
