package de.ingrid.ingridapi.admin.ui

import de.ingrid.ingridapi.core.services.IngridMetaEntry
import kotlinx.html.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object AdminComponents {
    private val jsonConfig = Json { prettyPrint = true }

    fun FlowContent.renderManagedCard(
        index: String,
        entry: IngridMetaEntry,
        docCount: Long?,
        config: JsonObject?,
        root: String,
    ) {
        val displayName = entry.dataSourceName ?: entry.indexId ?: index

        article(classes = "card") {
            // LEFT: Aktivierungs-Umschalter
            div(classes = "toggle") {
                form(action = "$root/admin/meta/${entry.docId}/active", method = FormMethod.post) {
                    hiddenInput(name = "active") { value = (!entry.active).toString() }
                    button(type = ButtonType.submit, classes = if (entry.active) "btn-on" else "btn-off") {
                        +(if (entry.active) "AN" else "AUS")
                    }
                }
            }

            // CENTER: Info-Block
            div(classes = "info") {
                div(classes = "name") { strong { +displayName } }
                div(classes = "index-name") { code { +index } }
                div(classes = "meta-line") {
                    span(classes = "metric") {
                        span(classes = "label") { +"Dokumente: " }
                        +(docCount?.toString() ?: "?")
                    }
                    span(classes = "metric") {
                        span(classes = "label") { +"Zuletzt indexiert: " }
                        +(formatTimestamp(entry.lastIndexed) ?: "—")
                    }
                }

                if (config != null) {
                    renderConfigDetails(config)
                }
            }

            // RIGHT: Löschen
            div(classes = "delete") {
                form(action = "$root/admin/indices/$index/delete", method = FormMethod.post) {
                    onClick =
                        "return confirm('Index \\'$index\\' wirklich löschen? Dies kann nicht rückgängig gemacht werden.');"
                    button(type = ButtonType.submit, classes = "btn-delete") { +"Löschen" }
                }
            }
        }
    }

    fun FlowContent.renderCompactRow(
        index: String,
        docCount: Long?,
        config: JsonObject?,
        root: String,
    ) {
        div(classes = "compact-row-container") {
            div(classes = "compact-row") {
                div(classes = "compact-name") { +index }
                div(classes = "compact-count") {
                    span(classes = "label") { +"Dokumente: " }
                    +(docCount?.toString() ?: "?")
                }
                div(classes = "delete") {
                    form(action = "$root/admin/indices/$index/delete", method = FormMethod.post) {
                        onClick =
                            "return confirm('Index \\'$index\\' wirklich löschen? Dies kann nicht rückgängig gemacht werden.');"
                        button(type = ButtonType.submit, classes = "btn-delete") { +"Löschen" }
                    }
                }
            }
            /*if (config != null) {
                div(classes = "compact-config") {
                    renderConfigDetails(config)
                }
            }*/
        }
    }

    private fun FlowContent.renderConfigDetails(config: JsonObject) {
        details(classes = "config-details") {
            summary { +"Mapping & Settings" }
            div(classes = "config-view") {
                val mapping = config["mappings"]?.jsonObject
                val settings = config["settings"]?.jsonObject

                if (mapping != null) {
                    h4 { +"Mapping" }
                    pre { code { +jsonConfig.encodeToString(JsonObject.serializer(), mapping) } }
                }
                if (settings != null) {
                    h4 { +"Settings" }
                    pre { code { +jsonConfig.encodeToString(JsonObject.serializer(), settings) } }
                }
            }
        }
    }

    private fun formatTimestamp(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val zone = java.time.ZoneId.systemDefault()
        val formatter =
            java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(zone)
        // 1) Numerischer Zeitstempel in Millisekunden
        value.toLongOrNull()?.let { millis ->
            return formatter.format(java.time.Instant.ofEpochMilli(millis))
        }
        // 2) ISO-8601 (z.B. "2024-05-13T10:15:30Z" oder "2024-05-13T10:15:30")
        return try {
            val instant =
                try {
                    java.time.Instant.parse(value)
                } catch (_: Exception) {
                    java.time.LocalDateTime
                        .parse(value)
                        .atZone(zone)
                        .toInstant()
                }
            formatter.format(instant)
        } catch (_: Exception) {
            value
        }
    }

    fun FlowContent.renderPagination(
        root: String,
        q: String?,
        currentPage: Int,
        totalHits: Long,
        pageSize: Int,
    ) {
        val totalPages = kotlin.math.ceil(totalHits.toDouble() / pageSize).toInt()
        if (totalPages <= 1) return

        val encodedQ = java.net.URLEncoder.encode(q ?: "", "UTF-8")

        nav {
            attributes["aria-label"] = "pagination"
            ul {
                // Previous
                li {
                    if (currentPage > 1) {
                        a(href = "$root/admin/search?q=$encodedQ&page=${currentPage - 1}") { +"«" }
                    } else {
                        a {
                            style = "pointer-events: none; opacity: 0.5;"
                            +"«"
                        }
                    }
                }

                val startPage = (currentPage - 2).coerceAtLeast(1)
                val endPage = (currentPage + 2).coerceAtMost(totalPages)

                if (startPage > 1) {
                    li { a(href = "$root/admin/search?q=$encodedQ&page=1") { +"1" } }
                    if (startPage > 2) li { span { +"..." } }
                }

                for (p in startPage..endPage) {
                    li {
                        if (p == currentPage) {
                            a(href = "#", classes = "outline") {
                                attributes["aria-current"] = "page"
                                attributes["onclick"] = "return false;"
                                +p.toString()
                            }
                        } else {
                            a(href = "$root/admin/search?q=$encodedQ&page=$p") { +p.toString() }
                        }
                    }
                }

                if (endPage < totalPages) {
                    if (endPage < totalPages - 1) li { span { +"..." } }
                    li { a(href = "$root/admin/search?q=$encodedQ&page=$totalPages") { +totalPages.toString() } }
                }

                // Next
                li {
                    if (currentPage < totalPages) {
                        a(href = "$root/admin/search?q=$encodedQ&page=${currentPage + 1}") { +"»" }
                    } else {
                        a {
                            style = "pointer-events: none; opacity: 0.5;"
                            +"»"
                        }
                    }
                }
            }
        }
    }

    val CSS =
        """
        :root {
            --pico-primary: #1565c0;
            --pico-font-size: 14px;
        }
        
        body { background-color: #f4f7f9; }
        
        header {
            background-color: #fff;
            border-bottom: 1px solid var(--pico-muted-border-color);
            margin-bottom: 2rem;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }
        header nav {
            padding-top: 0.5rem;
            padding-bottom: 0.5rem;
        }
        header strong {
            color: var(--pico-primary);
        }
        
        h2 { font-size: 1.5rem; margin-top: 1.5rem; }
        h3 { font-size: 1.25rem; }
        
        .msg { padding: 8px 12px; border-radius: 4px; margin: 10px 0; font-weight: 500; font-size: 0.9em; }
        .msg.ok  { background: #e6f4ea; color: #14532d; border: 1px solid #34a853; }
        .msg.err { background: #fdecea; color: #7f1d1d; border: 1px solid #d93025; }
        
        .cards { display: flex; flex-direction: column; gap: 0; }
        article.card {
            display: flex;
            align-items: center;
            gap: 16px;
            margin-top: 0;
            margin-bottom: 0.75rem;
            padding: 0.75rem 1rem;
            border-left: 4px solid var(--pico-primary);
            box-shadow: var(--pico-card-box-shadow);
        }
        .card .toggle { flex: 0 0 auto; }
        .card .info   { flex: 1 1 auto; min-width: 0; }
        .card .delete { flex: 0 0 auto; display: none;}
        .card:hover .delete { display: block;}
        .card .name {
            font-size: 1.1em;
            color: var(--pico-primary);
            line-height: 1.2;
        }
        .card .index-name {
            font-family: var(--pico-font-family-monospace);
            font-size: 0.85em;
            color: var(--pico-muted-color);
            margin-top: 1px;
        }
        .card .meta-line { margin-top: 6px; font-size: 0.88em; }
        .card .meta-line .metric { margin-right: 18px; }
        .card .meta-line .label  { color: var(--pico-muted-color); font-weight: 500; }
        
        button.btn-on  { --pico-background-color: #1b873f; --pico-border-color: #14672f; --pico-color: #fff; }
        button.btn-off { --pico-background-color: #5f6b7a; --pico-border-color: #4a5562; --pico-color: #fff; }
        button.btn-delete {
            --pico-background-color: #b3261e;
            --pico-border-color: #8c1d18;
            --pico-color: #fff;
        }
        button {
          //--pico-form-element-spacing-vertical: 0.2rem;
        }
        
        article.compact-list { padding: 0; overflow: hidden; margin-top: 1rem; }
        .compact-row-container { border-bottom: 1px solid var(--pico-muted-border-color); }
        .compact-row-container:last-child { border-bottom: none; }
        .compact-row {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 6px 12px;
        }
        .compact-row .compact-name {
            flex: 1 1 auto;
            font-size: 0.9em;
            font-family: var(--pico-font-family-monospace);
        }
        .compact-row .compact-count { flex: 0 0 auto; font-size: 0.88em; }
        .compact-row .label { color: var(--pico-muted-color); font-weight: 500; }
        
        .config-details { margin-top: 10px; font-size: 0.85em; }
        .config-details summary { cursor: pointer; color: var(--pico-primary); }
        .config-view h4 { margin-top: 10px; margin-bottom: 5px; font-size: 1.1em; }
        .compact-config { padding: 0 12px 10px 12px; }
        
        .search-box { display: flex; gap: 10px; margin-bottom: 20px; }
        .search-box input { margin-bottom: 0; font-size: 0.9em; }
        .search-box button { flex: 2;}
        
        article.result-item { margin-top: 0; margin-bottom: 0.75rem; padding: 0.75rem 1rem; }
        .result-meta { font-size: 0.82em; color: var(--pico-muted-color); margin-top: 4px; }
        
        pre { padding: 0.75rem; overflow: auto; max-height: 600px; font-size: 0.85em; }
        .doc-meta { margin-bottom: 8px; font-size: 0.85em; }

        nav[aria-label="pagination"] { display: flex; justify-content: center; margin-top: 1.5rem; }
        nav[aria-label="pagination"] ul { list-style: none; display: flex; gap: 4px; padding: 0; align-items: center; }
        nav[aria-label="pagination"] li { margin: 0; padding: 0; }
        nav[aria-label="pagination"] a { 
            display: inline-block;
            padding: 3px 10px; 
            text-decoration: none; 
            line-height: 1.4;
            font-size: 0.9em;
        }
        nav[aria-label="pagination"] a.outline { font-weight: bold; }
        nav[aria-label="pagination"] span { padding: 0 6px; color: var(--pico-muted-color); font-size: 0.9em; }
        """.trimIndent()
}
