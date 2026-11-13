package de.ingrid.ingridapi.opensearch.service

import de.ingrid.ingridapi.core.services.asSafeString
import io.ktor.server.config.ApplicationConfig

/**
 * Channel settings that are only used in UVP mode (legacy configuration keys
 * `opensearch.channel.language` and `opensearch.channel.copyright` of ingrid-interface-search).
 */
data class UvpChannelConfig(
    val language: String = "",
    val copyright: String = "",
)

/**
 * UVP-specific extension of the OpenSearch RSS response.
 *
 * This is the migration of the UVP parts of `de.ingrid.iface.opensearch.OpensearchServlet`
 * (UVP-Verbund cooperation). It is activated with the legacy request parameter `uvp=1` and
 * changes the RSS response as follows:
 * - the OpenSearch response elements (`opensearch:totalResults`, `opensearch:startIndex`,
 *   `opensearch:itemsPerPage`, `opensearch:Query`) are replaced by a channel `pubDate` with
 *   the modification time of the most recent hit and by optional `language`/`copyright`
 *   elements
 * - every item additionally contains `guid`, `author` (from `uvp_address`), `pubDate` and
 *   `uvp:latest-phase` (mapped from the last entry of `uvp_steps`)
 * - the `relevance:score` element is omitted
 *
 * Everything UVP-specific is bundled in this file so that the feature can be removed in one
 * go as soon as it is not needed anymore:
 * 1. delete this file,
 * 2. remove the call sites in [RssRenderer] and `Routes.kt` that are marked with `--- UVP ---`,
 * 3. remove the `uvp` section from the `opensearch` settings in `application.yaml` and the
 *    `uvp` parameter from the Swagger description of the query endpoint.
 */
object UvpSupport {

    /** The legacy request parameter that activates the UVP behaviour (`uvp=1`). */
    const val REQUEST_PARAM = "uvp"

    const val NAMESPACE_PREFIX = "uvp"

    const val NAMESPACE_URI = "http://www.uvp-verbund.de/opensearch/extension/1.0"

    /** Document field with the UVP phases of the procedure, joined with ", ". */
    const val FIELD_STEPS = "uvp_steps"

    /** Document field with the responsible address of the UVP procedure. */
    const val FIELD_ADDRESS = "uvp_address"

    private val PHASES =
        mapOf(
            "phase1" to "Öffentliche Auslegung",
            "phase2" to "Erörterungstermin",
            "phase3" to "Entscheidung über die Zulassung",
            "scopeOfInvestigation" to "Unterrichtung über den Untersuchungsrahmen",
        )

    /** Whether the request shall be served with the UVP-specific response (`uvp=1`). */
    fun isRequested(params: OpenSearchParams): Boolean = params.withUvp

    /** Parses the UVP channel settings from the `ktor.opensearch.uvp` configuration section. */
    fun parseChannelConfig(config: ApplicationConfig): UvpChannelConfig =
        UvpChannelConfig(
            language = config.propertyOrNull("ktor.opensearch.uvp.language")?.getString() ?: "",
            copyright = config.propertyOrNull("ktor.opensearch.uvp.copyright")?.getString() ?: "",
        )

    /** The `xmlns:uvp` declaration that is added to the root element in UVP mode. */
    fun namespaceDeclaration(): String = " xmlns:$NAMESPACE_PREFIX=\"$NAMESPACE_URI\""

    /**
     * Renders the channel elements that replace the OpenSearch response elements in UVP mode:
     * the `pubDate` of the most recent hit and the optional language/copyright elements.
     *
     * @param mostRecentHit the first hit of the result (the most recent one), `null` if the
     *   search has no result
     */
    fun renderChannelElements(
        config: UvpChannelConfig,
        mostRecentHit: OpenSearchHit?,
        sb: StringBuilder,
    ) {
        val modTime = mostRecentHit?.let { lastModifiedOf(it) }.orEmpty()
        if (modTime.isNotEmpty()) sb.append("<pubDate>${xmlEscape(modTime)}</pubDate>")
        if (config.language.isNotEmpty()) sb.append("<language>${xmlEscape(config.language)}</language>")
        if (config.copyright.isNotEmpty()) sb.append("<copyright>${xmlEscape(config.copyright)}</copyright>")
    }

    /** Renders the UVP elements of a single item: `guid`, `author`, `pubDate` and `uvp:latest-phase`. */
    fun renderItemElements(
        hit: OpenSearchHit,
        sb: StringBuilder,
    ) {
        sb.append("<guid>${xmlEscape(hit.id)}</guid>")
        sb.append("<author>${xmlEscape(hit.source[FIELD_ADDRESS].asSafeString())}</author>")
        val modTime = lastModifiedOf(hit)
        if (modTime.isNotEmpty()) sb.append("<pubDate>${xmlEscape(modTime)}</pubDate>")
        val steps = hit.source[FIELD_STEPS].asSafeString()
        if (steps.isNotEmpty()) {
            latestPhase(steps)?.let {
                sb.append("<$NAMESPACE_PREFIX:latest-phase>${xmlEscape(it)}</$NAMESPACE_PREFIX:latest-phase>")
            }
        }
    }

    /** Maps the last phase of the `uvp_steps` field to its UVP phase name, `null` if unknown. */
    internal fun latestPhase(steps: String): String? = PHASES[steps.split(", ").lastOrNull()?.trim() ?: ""]
}
