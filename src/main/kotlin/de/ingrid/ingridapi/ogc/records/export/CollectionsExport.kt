package de.ingrid.ingridapi.ogc.records.export

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable

enum class ExportFormat { JSON, HTML }

fun parseExportFormat(param: String?): ExportFormat =
    when (param?.lowercase()) {
        "html", "text/html" -> ExportFormat.HTML
        else -> ExportFormat.JSON
    }

@Serializable
data class CollectionSummary(
    val id: String,
    val title: String,
    val description: String? = null,
)

@Serializable
data class CollectionsResponse(
    val collections: List<CollectionSummary>,
)

interface CollectionsExporter {
    suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
    )
}

class JsonCollectionsExporter : CollectionsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
    ) {
        call.respond(CollectionsResponse(collections))
    }
}

class HtmlCollectionsExporter : CollectionsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        collections: List<CollectionSummary>,
    ) {
        val html =
            buildString {
                append(
                    """
                    <!DOCTYPE html>
                    <html lang=\"en\">
                    <head>
                      <meta charset=\"utf-8\"/>
                      <title>OGC API - Records: Collections</title>
                      <style>
                        body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif; margin:20px}
                        table{border-collapse:collapse; width:100%}
                        th,td{border:1px solid #ddd; padding:8px}
                        th{background:#f5f5f5; text-align:left}
                        caption{font-weight:600; margin-bottom:8px}
                        code{background:#f6f8fa; padding:2px 4px; border-radius:4px}
                      </style>
                    </head>
                    <body>
                      <h1>Collections</h1>
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
                    append("<td><code>").append(escapeHtml(id)).append("</code></td>")
                    append("<td>").append(escapeHtml(title)).append("</td>")
                    append("<td>").append(escapeHtml(desc)).append("</td>")
                    append("</tr>")
                }
                append(
                    """
                        </tbody>
                      </table>
                    </body>
                    </html>
                    """.trimIndent(),
                )
            }
        call.respondText(html, ContentType.Text.Html)
    }

    private fun escapeHtml(text: String): String =
        buildString(text.length) {
            for (ch in text) {
                when (ch) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
}

object CollectionsExporterFactory {
    fun create(format: ExportFormat): CollectionsExporter =
        when (format) {
            ExportFormat.JSON -> JsonCollectionsExporter()
            ExportFormat.HTML -> HtmlCollectionsExporter()
        }
}
