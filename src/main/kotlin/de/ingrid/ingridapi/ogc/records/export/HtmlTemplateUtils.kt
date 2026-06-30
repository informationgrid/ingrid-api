package de.ingrid.ingridapi.ogc.records.export

import de.ingrid.ingridapi.ogc.records.Link

object HtmlTemplateUtils {
    val SHARED_STYLES = """
        :root {
          --primary-color: #0056b3;
          --bg-color: #f8f9fa;
          --text-color: #333;
          --card-bg: #fff;
          --border-color: #dee2e6;
        }
        body {
          font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
          line-height: 1.6;
          color: var(--text-color);
          margin: 0;
          padding: 20px;
          background-color: var(--bg-color);
        }
        .container {
          max-width: 900px;
          margin: 0 auto;
        }
        h1, h2, h3 { color: var(--primary-color); }
        .card {
          background: var(--card-bg);
          border: 1px solid var(--border-color);
          border-radius: 8px;
          padding: 20px;
          margin-bottom: 20px;
          box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }
        nav { margin-bottom: 20px; font-size: 0.9em; }
        nav a { color: var(--primary-color); text-decoration: none; }
        nav a:hover { text-decoration: underline; }
        ul { list-style: none; padding: 0; }
        li { margin-bottom: 12px; border-bottom: 1px solid #eee; padding-bottom: 8px; }
        li:last-child { border-bottom: none; }
        .link-rel {
          font-weight: bold;
          display: inline-block;
          width: 120px;
          color: #666;
          font-size: 0.9em;
        }
        code {
          background: #f1f3f5;
          padding: 2px 6px;
          border-radius: 4px;
          font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
          font-size: 0.9em;
        }
        .tag {
          display: inline-block;
          padding: 2px 8px;
          border-radius: 12px;
          font-size: 0.8em;
          background: #e9ecef;
          margin-left: 10px;
        }
        table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }
        th, td { border: 1px solid var(--border-color); padding: 12px; text-align: left; vertical-align: top; }
        th { background: #f1f3f5; color: var(--text-color); font-weight: 600; width: 200px; }
        caption { font-weight: 600; margin-bottom: 8px; text-align: left; color: var(--primary-color); }
        .paging { margin-top: 20px; display: flex; gap: 10px; align-items: center; }
        .paging a { padding: 8px 16px; border: 1px solid var(--border-color); text-decoration: none; color: var(--primary-color); border-radius: 4px; background: var(--card-bg); }
        .paging a:hover { background: #e9ecef; }
        .paging .current { font-weight: bold; color: var(--text-color); }
        #map { height: 400px; width: 100%; margin-top: 20px; border: 1px solid var(--border-color); border-radius: 8px; }
    """.trimIndent()

    fun renderHtmlPage(
        title: String,
        breadcrumbs: List<Pair<String, String>> = emptyList(),
        content: String,
        headExtra: String = "",
    ): String {
        val breadcrumbHtml =
            if (breadcrumbs.isNotEmpty()) {
                """
                <nav>
                    ${breadcrumbs.joinToString(" / ") { (label, url) -> "<a href=\"$url\">${escapeHtml(label)}</a>" }}
                </nav>
                """.trimIndent()
            } else {
                ""
            }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>${escapeHtml(title)}</title>
              <style>
                $SHARED_STYLES
              </style>
              $headExtra
            </head>
            <body>
              <div class="container">
                $breadcrumbHtml
                <h1>${escapeHtml(title)}</h1>
                $content
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun renderLinksSection(links: List<Link>): String {
        return """
            <div class="card">
              <h2>Links</h2>
              <ul>
                ${links.joinToString("") { link ->
            """
                    <li>
                      <span class="link-rel">${escapeHtml(link.rel)}</span>
                      <a href="${escapeHtml(link.href)}">${escapeHtml(link.title ?: link.href)}</a>
                      ${if (link.type != null) "<span class=\"tag\">${escapeHtml(link.type)}</span>" else ""}
                    </li>
                    """.trimIndent()
        }}
              </ul>
            </div>
        """.trimIndent()
    }

    fun escapeHtml(text: String): String =
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
