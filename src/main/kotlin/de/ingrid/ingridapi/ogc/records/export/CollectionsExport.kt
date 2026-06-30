package de.ingrid.ingridapi.ogc.records.export

import de.ingrid.ingridapi.ogc.records.CollectionDetail
import de.ingrid.ingridapi.ogc.records.Conformance
import de.ingrid.ingridapi.ogc.records.Link
import de.ingrid.ingridapi.ogc.records.export.HtmlTemplateUtils.escapeHtml
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable

enum class ExportFormat(
    val paramValue: String,
    val mediaType: String,
) {
    JSON("json", "application/json"),
    HTML("html", "text/html"),
}

val SUPPORTED_COLLECTION_FORMATS: List<String> = ExportFormat.entries.map { it.paramValue }

sealed class ExportFormatResult {
    data class Ok(val format: ExportFormat) : ExportFormatResult()

    /** The `f` query parameter was provided but unknown. */
    data class InvalidParam(val value: String) : ExportFormatResult()

    /** No `f` was given and the Accept header is not satisfiable. */
    data class NotAcceptable(val acceptHeader: String) : ExportFormatResult()
}

fun parseExportFormat(
    param: String?,
    acceptHeader: String? = null,
): ExportFormat =
    when (val r = parseExportFormatResult(param, acceptHeader)) {
        is ExportFormatResult.Ok -> r.format
        else -> ExportFormat.HTML
    }

fun parseExportFormatResult(
    param: String?,
    acceptHeader: String? = null,
): ExportFormatResult {
    if (param != null) {
        val match = ExportFormat.entries.firstOrNull { it.paramValue.equals(param, ignoreCase = true) }
        return if (match != null) ExportFormatResult.Ok(match) else ExportFormatResult.InvalidParam(param)
    }
    if (acceptHeader.isNullOrBlank()) {
        return ExportFormatResult.Ok(ExportFormat.JSON)
    }
    val lower = acceptHeader.lowercase()
    // Exact known media types
    ExportFormat.entries.firstOrNull { lower.contains(it.mediaType) }?.let { return ExportFormatResult.Ok(it) }
    // Browser-style wildcards fall back to HTML (best practice for user agents)
    if (lower.contains("*/*")) return ExportFormatResult.Ok(ExportFormat.HTML)
    return ExportFormatResult.NotAcceptable(acceptHeader)
}

@Serializable
data class CollectionSummary(
    val id: String,
    val title: String,
    val description: String? = null,
    val links: List<Link>,
)

@Serializable
data class CollectionsResponse(
    val links: List<Link>,
    val collections: List<CollectionSummary>,
)

@Serializable
data class LandingPage(
    val title: String,
    val description: String? = null,
    val links: List<Link>,
)

interface CollectionsExporter {
    suspend fun respondLandingPage(
        call: ApplicationCall,
        landingPage: LandingPage,
    )

    suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
        links: List<Link> = emptyList(),
    )

    suspend fun respond(
        call: ApplicationCall,
        collection: CollectionDetail,
    )

    suspend fun respondConformance(
        call: ApplicationCall,
        conformance: Conformance,
    )
}

class JsonCollectionsExporter : CollectionsExporter {
    override suspend fun respondLandingPage(
        call: ApplicationCall,
        landingPage: LandingPage,
    ) {
        call.respond(landingPage)
    }

    override suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
        links: List<Link>,
    ) {
        call.respond(CollectionsResponse(links, collections))
    }

    override suspend fun respond(
        call: ApplicationCall,
        collection: CollectionDetail,
    ) {
        call.respond(collection)
    }

    override suspend fun respondConformance(
        call: ApplicationCall,
        conformance: Conformance,
    ) {
        call.respond(conformance)
    }
}

class HtmlCollectionsExporter : CollectionsExporter {
    override suspend fun respondLandingPage(
        call: ApplicationCall,
        landingPage: LandingPage,
    ) {
        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <p>${escapeHtml(landingPage.description.orEmpty())}</p>
                    </div>
                    
                    <div class="card">
                      <h2>API Overview</h2>
                      <p>
                        This service implements the <strong>OGC API - Records</strong> standard. 
                        It provides discovery and access to metadata records describing geospatial data and services.
                      </p>
                      <p>
                        You can explore the available collections of records, search for specific items using spatial filters, 
                        and retrieve metadata in various formats.
                      </p>
                    </div>
    
                    <div class="card">
                      <h2>Common Search Parameters</h2>
                      <p>When querying record collections, you can use the following parameters to filter your results:</p>
                      <ul>
                        <li><code>bbox</code>: Spatial filter using a bounding box in <code>minLon,minLat,maxLon,maxLat</code> format.</li>
                        <li><code>limit</code>: Maximum number of records to return (default is 10).</li>
                        <li><code>offset</code>: Number of records to skip for pagination.</li>
                        <li><code>format</code> / <code>f</code>: The output format (<code>json</code> or <code>html</code>).</li>
                      </ul>
                    </div>
                    """.trimIndent(),
                )
                append(HtmlTemplateUtils.renderLinksSection(landingPage.links))
            }
        val html = HtmlTemplateUtils.renderHtmlPage(landingPage.title, emptyList(), content)
        call.respondText(html, ContentType.Text.Html)
    }

    override suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
        links: List<Link>,
    ) {
        val root =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <table>
                        <caption>Available record collections</caption>
                        <thead>
                          <tr><th>ID</th><th>Title</th><th>Description</th></tr>
                        </thead>
                        <tbody>
                    """.trimIndent(),
                )
                for (c in collections) {
                    val id = c.id
                    val title = c.title.ifEmpty { id }
                    val desc = c.description.orEmpty()
                    append("<tr>")
                    append("<td><a href=\"$root/ogc/records/collections/")
                        .append(escapeHtml(id))
                        .append("?f=html\"><code>")
                        .append(escapeHtml(id))
                        .append("</code></a></td>")
                    append("<td>").append(escapeHtml(title)).append("</td>")
                    append("<td>").append(escapeHtml(desc)).append("</td>")
                    append("</tr>")
                }
                append(
                    """
                        </tbody>
                      </table>
                    </div>
                    """.trimIndent(),
                )
                append(HtmlTemplateUtils.renderLinksSection(links))
            }
        val breadcrumbs = listOf("Home" to "$root/ogc/records")
        val html = HtmlTemplateUtils.renderHtmlPage("OGC API - Records: Collections", breadcrumbs, content)
        call.respondText(html, ContentType.Text.Html)
    }

    override suspend fun respond(
        call: ApplicationCall,
        collection: CollectionDetail,
    ) {
        val root =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <p>${escapeHtml(collection.description)}</p>
                      <table>
                        <tr><th>ID</th><td><code>${escapeHtml(collection.id)}</code></td></tr>
                        <tr><th>Item Type</th><td>${escapeHtml(collection.itemType)}</td></tr>
                      </table>
                    </div>
                    """.trimIndent(),
                )
                append(HtmlTemplateUtils.renderLinksSection(collection.links))
            }
        val breadcrumbs =
            listOf(
                "Home" to "$root/ogc/records",
                "Collections" to "$root/ogc/records/collections?f=html",
            )
        val html = HtmlTemplateUtils.renderHtmlPage("OGC API - Records: ${collection.title}", breadcrumbs, content)
        call.respondText(html, ContentType.Text.Html)
    }

    override suspend fun respondConformance(
        call: ApplicationCall,
        conformance: Conformance,
    ) {
        val root =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <p>This implementation conforms to the following OGC API conformance classes:</p>
                      <ul>
                    """.trimIndent(),
                )
                for (c in conformance.conformsTo) {
                    append("<li>")
                    append("<span class=\"link-rel\">conformsTo</span>")
                    append("<code>").append(escapeHtml(c)).append("</code>")
                    append("</li>")
                }
                append(
                    """
                      </ul>
                    </div>
                    """.trimIndent(),
                )
            }
        val breadcrumbs = listOf("Home" to "$root/ogc/records")
        val html = HtmlTemplateUtils.renderHtmlPage("OGC API - Records: Conformance", breadcrumbs, content)
        call.respondText(html, ContentType.Text.Html)
    }
}

object CollectionsExporterFactory {
    fun create(format: ExportFormat): CollectionsExporter =
        when (format) {
            ExportFormat.JSON -> JsonCollectionsExporter()
            ExportFormat.HTML -> HtmlCollectionsExporter()
        }
}
