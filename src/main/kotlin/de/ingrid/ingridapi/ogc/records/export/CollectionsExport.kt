package de.ingrid.ingridapi.ogc.records.export

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

enum class ExportFormat { JSON, HTML }

fun parseExportFormat(param: String?): ExportFormat = when (param?.lowercase()) {
  "html", "text/html" -> ExportFormat.HTML
  else -> ExportFormat.JSON
}

interface CollectionsExporter {
  suspend fun respond(call: ApplicationCall, collections: List<Map<String, Any?>>)
}

class JsonCollectionsExporter : CollectionsExporter {
  override suspend fun respond(call: ApplicationCall, collections: List<Map<String, Any?>>) {
    call.respond(mapOf("collections" to collections))
  }
}

class HtmlCollectionsExporter : CollectionsExporter {
  override suspend fun respond(call: ApplicationCall, collections: List<Map<String, Any?>>) {
    val html = buildString {
      append("""
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
      """.trimIndent())
      for (c in collections) {
        val id = c["id"]?.toString() ?: ""
        val title = c["title"]?.toString() ?: id
        val desc = c["description"]?.toString() ?: ""
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
        """.trimIndent()
      )
    }
    call.respondText(html, ContentType.Text.Html)
  }

  private fun escapeHtml(text: String): String = buildString(text.length) {
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
  fun create(format: ExportFormat): CollectionsExporter = when (format) {
    ExportFormat.JSON -> JsonCollectionsExporter()
    ExportFormat.HTML -> HtmlCollectionsExporter()
  }
}
