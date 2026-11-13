package de.ingrid.ingridapi.opensearch.service

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Translates a parsed InGrid query into an Elasticsearch query DSL document.
 *
 * The plain search terms and phrases are matched against all text fields of the index
 * documents (`"fields": ["*"]`), field searches are translated into `match` queries and
 * document id searches into `term` queries on `_id`.
 */
object EsQueryBuilder {

    /** Builds the raw Elasticsearch query body, including paging via `from`/`size`. */
    fun build(
        parsed: ParsedIngridQuery,
        from: Int,
        size: Int,
    ): JsonElement =
        buildJsonObject {
            put("from", from)
            put("size", size)
            put("query", buildQuery(parsed))
        }

    internal fun buildQuery(parsed: ParsedIngridQuery): JsonElement {
        val activeGroups =
            parsed.groups.filter { it.must.isNotEmpty() || it.mustNot.isNotEmpty() }
        return when (activeGroups.size) {
            0 -> buildJsonObject { put("match_all", buildJsonObject { }) }
            1 -> buildGroup(activeGroups.first())
            else ->
                buildJsonObject {
                    put(
                        "bool",
                        buildJsonObject {
                            put(
                                "should",
                                buildJsonArray {
                                    activeGroups.forEach { add(buildGroup(it)) }
                                },
                            )
                            put("minimum_should_match", 1)
                        },
                    )
                }
        }
    }

    private fun buildGroup(group: QueryGroup): JsonElement =
        buildJsonObject {
            put(
                "bool",
                buildJsonObject {
                    if (group.must.isNotEmpty()) {
                        put(
                            "must",
                            buildJsonArray {
                                group.must.forEach { add(toClause(it)) }
                            },
                        )
                    }
                    if (group.mustNot.isNotEmpty()) {
                        put(
                            "must_not",
                            buildJsonArray {
                                group.mustNot.forEach { add(toClause(it)) }
                            },
                        )
                    }
                },
            )
        }

    private fun toClause(condition: QueryCondition): JsonElement =
        when (condition) {
            is SearchTerm ->
                buildJsonObject {
                    put(
                        "multi_match",
                        buildJsonObject {
                            put("query", condition.text)
                            put("fields", buildJsonArray { add("*") })
                            put("lenient", true)
                        },
                    )
                }

            is SearchPhrase ->
                buildJsonObject {
                    put(
                        "multi_match",
                        buildJsonObject {
                            put("query", condition.text)
                            put("type", "phrase")
                            put("fields", buildJsonArray { add("*") })
                            put("lenient", true)
                        },
                    )
                }

            is FieldSearch ->
                buildJsonObject {
                    put(
                        "match",
                        buildJsonObject {
                            put(
                                condition.field,
                                buildJsonObject { put("query", condition.value) },
                            )
                        },
                    )
                }

            is DocumentIdSearch ->
                buildJsonObject {
                    put(
                        "term",
                        buildJsonObject {
                            put("_id", condition.value)
                        },
                    )
                }
        }
}
