package de.ingrid.ingridapi.opensearch.service

/**
 * Elements of the InGrid query language that are relevant for the OpenSearch interface.
 *
 * This is a pragmatic migration of the behaviour of `de.ingrid.utils.queryparser.QueryStringParser`
 * (ingrid-interface-search), reduced to the constructs OpenSearch clients actually use:
 * - plain search terms: `boden`
 * - quoted phrases: `"wind energy"`
 * - field searches: `title:wasser`
 * - required terms: `+boden` and excluded terms: `-solar`
 * - the boolean operators `AND`, `OR`, `NOT` (uppercase)
 * - meta terms that only carry iBus/portal information (`ranking:score`, `iplugs:"..."`,
 *   `buffer:...`) are accepted for compatibility but dropped, because they have no equivalent
 *   in the Elasticsearch index.
 */
sealed interface QueryCondition

/** A plain search term, matched against any text field of the index documents. */
data class SearchTerm(
    val text: String,
) : QueryCondition

/** A quoted phrase, matched as a phrase against any text field of the index documents. */
data class SearchPhrase(
    val text: String,
) : QueryCondition

/** A search restricted to a single field of the index documents. */
data class FieldSearch(
    val field: String,
    val value: String,
) : QueryCondition

/** A search for a document by its unique identifier (maps to the `_id` field). */
data class DocumentIdSearch(
    val value: String,
) : QueryCondition

/**
 * One AND-composed group of conditions. Multiple groups are combined with OR
 * (`"a b OR c d"` results in `(a AND b) OR (c AND d)`).
 */
data class QueryGroup(
    val must: List<QueryCondition>,
    val mustNot: List<QueryCondition>,
)

/** The parsed result of an InGrid query string. */
data class ParsedIngridQuery(
    val groups: List<QueryGroup>,
)

object IngridQueryParser {

    /** Fields that only carry iBus/portal meta information and are ignored during the search. */
    private val ignoredFields = setOf("ranking", "iplugs", "buffer")

    /** Fields that identify a single document by its unique id. */
    private val documentIdFields = setOf("docuuid", "_id", "id", "t01_object.obj_id")

    private val fieldPattern = Regex("^([^\\s:\"]+):(.*)$")

    /**
     * Parses an InGrid query string as delivered in the `q` parameter of the OpenSearch
     * interface, e.g. `wasser datatype:default ranking:score`.
     */
    fun parse(queryString: String): ParsedIngridQuery {
        val mustGroups = mutableListOf(mutableListOf<QueryCondition>())
        val mustNotGroups = mutableListOf(mutableListOf<QueryCondition>())
        var pendingNegation = false

        for (token in tokenize(queryString)) {
            when (token) {
                "AND" -> {
                    // AND is the default operator between conditions
                }

                "OR" -> {
                    mustGroups.add(mutableListOf())
                    mustNotGroups.add(mutableListOf())
                    pendingNegation = false
                }

                "NOT" -> pendingNegation = true

                else -> {
                    val parsedToken = toCondition(token)
                    if (parsedToken != null) {
                        val target =
                            if (pendingNegation || parsedToken.excluded) mustNotGroups.last() else mustGroups.last()
                        target.add(parsedToken.condition)
                    }
                    pendingNegation = false
                }
            }
        }

        val groups =
            mustGroups
                .mapIndexed { index, must -> QueryGroup(must.toList(), mustNotGroups[index].toList()) }
                .filter { it.must.isNotEmpty() || it.mustNot.isNotEmpty() }
        return ParsedIngridQuery(groups)
    }

    /** Splits the query string into tokens, keeping quoted phrases together. */
    internal fun tokenize(queryString: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in queryString) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }

                (char.isWhitespace() || char == '+') && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }

                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /** A condition together with the flag whether it was excluded with `-` or `NOT`. */
    internal data class ParsedToken(
        val condition: QueryCondition,
        val excluded: Boolean,
    )

    /** Classifies a single token into a [ParsedToken], or `null` if it shall be ignored. */
    private fun toCondition(token: String): ParsedToken? {
        val excluded = token.startsWith("-")
        val stripped = token.removePrefix("-").removePrefix("+")

        if (stripped.isEmpty()) return null

        if (stripped.startsWith("\"") && stripped.endsWith("\"") && stripped.length >= 2) {
            val phrase = stripped.substring(1, stripped.length - 1)
            return if (phrase.isBlank()) null else ParsedToken(SearchPhrase(phrase), excluded)
        }

        val fieldMatch = fieldPattern.matchEntire(stripped)
        if (fieldMatch != null) {
            val field = fieldMatch.groupValues[1]
            val value = fieldMatch.groupValues[2].removeSurrounding("\"")
            if (value.isEmpty()) return null

            val normalizedField = field.lowercase()
            return when {
                normalizedField in ignoredFields -> null
                normalizedField in documentIdFields -> ParsedToken(DocumentIdSearch(value), excluded)
                else -> ParsedToken(FieldSearch(field, value), excluded)
            }
        }

        return ParsedToken(SearchTerm(stripped), excluded)
    }
}
