package de.ingrid.ingridapi.admin.ui

import de.ingrid.ingridapi.admin.ui.AdminComponents.renderCompactRow
import de.ingrid.ingridapi.admin.ui.AdminComponents.renderManagedCard
import de.ingrid.ingridapi.admin.ui.AdminComponents.renderPagination
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.IngridMetaEntry
import de.ingrid.ingridapi.core.services.SearchResult
import kotlinx.html.*
import kotlinx.serialization.json.*

object AdminPages {
    fun HTML.renderErrorPage(
        root: String,
        error: String?,
    ) {
        attributes["data-theme"] = "light"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("InGrid API – Administration (Fehler)")
            styleLink("https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
            style { unsafe { +AdminComponents.CSS } }
        }
        body {
            main(classes = "container") {
                h1 { +"InGrid API – Administration" }
                if (!error.isNullOrBlank()) {
                    div(classes = "msg err") { +error }
                }
                p {
                    +"Sie haben keine Berechtigung für diesen Bereich oder ein Sitzungsfehler ist aufgetreten."
                }
                div {
                    a(href = "$root/auth/login", classes = "btn-retry") {
                        attributes["role"] = "button"
                        +"Erneut versuchen"
                    }
                }
            }
        }
    }

    fun HTML.renderIndicesPage(
        root: String,
        managedEntries: List<IngridMetaEntry>,
        others: Map<String, Any?>,
        counts: Map<String, Long>,
        message: String?,
        error: String?,
        prefix: String = "",
        metaIndexName: String = "ingrid_meta",
    ) {
        adminLayout("Administration", root, activeTab = "indices") {
            p {
                +"Verwaltung der Elasticsearch-Indizes. Indizes, die in "
                code { +metaIndexName }
                +" referenziert sind, werden als Karten hervorgehoben."
            }
            if (prefix.isNotEmpty()) {
                p {
                    +"Konfigurierter Index-Präfix: "
                    code { +prefix }
                }
            }
            if (!message.isNullOrBlank()) {
                div(classes = "msg ok") { +message }
            }
            if (!error.isNullOrBlank()) {
                div(classes = "msg err") { +error }
            }

            val hasPrefix = prefix.isNotEmpty()
            val (prefixedManaged, otherManaged) = if (hasPrefix) {
                managedEntries.partition { it.linkedIndex?.startsWith(prefix) == true }
            } else {
                managedEntries to emptyList()
            }

            if (hasPrefix) {
                h2 { +"Verwaltete Indizes mit Präfix '$prefix'" }
                if (prefixedManaged.isEmpty()) {
                    p { +"Keine Indizes mit diesem Präfix in '$metaIndexName' referenziert." }
                } else {
                    renderManagedCards(prefixedManaged, counts, root)
                }

                h2 { +"Weitere verwaltete Indizes" }
                if (otherManaged.isEmpty()) {
                    p { +"Keine weiteren verwalteten Indizes vorhanden." }
                } else {
                    renderManagedCards(otherManaged, counts, root)
                }
            } else {
                h2 { +"Verwaltete Indizes" }
                if (managedEntries.isEmpty()) {
                    p { +"Keine Indizes in '$metaIndexName' referenziert." }
                } else {
                    renderManagedCards(managedEntries, counts, root)
                }
            }

            h2 { +"Weitere Indizes" }
            if (others.isEmpty()) {
                p { +"Keine weiteren Indizes vorhanden." }
            } else {
                article(classes = "compact-list") {
                    others.entries.sortedBy { it.key }.forEach { (index, _) ->
                        renderCompactRow(index, counts[index], root)
                    }
                }
            }
        }
    }

    fun HTML.renderSearchPage(
        root: String,
        q: String?,
        results: SearchResult?,
        page: Int = 1,
        pageSize: Int = 20,
    ) {
        adminLayout("Suche", root, activeTab = "search") {
            h2 { +"Suche" }
            form(action = "$root/admin/search", method = FormMethod.get) {
                div(classes = "search-box") {
                    input(type = InputType.text, name = "q") {
                        value = q ?: ""
                        placeholder = "Suchbegriff..."
                    }
                    button(type = ButtonType.submit) { +"Suchen" }
                }
            }

            if (results != null) {
                h3 { +"Ergebnisse (${results.totalHits})" }
                div(classes = "results") {
                    results.hits.forEach { hit ->
                        val obj = hit.jsonObject
                        val id = obj["_id"]?.jsonPrimitive?.content ?: "unknown"
                        val index = obj["_index"]?.jsonPrimitive?.content ?: "unknown"
                        val source = obj["_source"]?.jsonObject

                        // Try to find a title or name to show
                        val title =
                            source?.get("title")?.jsonPrimitive?.content
                                ?: source?.get("name")?.jsonPrimitive?.content
                                ?: id

                        article(classes = "result-item") {
                            a(href = "$root/admin/search/view?index=$index&id=$id") {
                                strong { +title }
                            }
                            div(classes = "result-meta") {
                                +"Index: $index | ID: $id"
                            }
                        }
                    }
                }
                renderPagination(root, q, page, results.totalHits, pageSize)
            }
        }
    }

    private val jsonConfig = Json { prettyPrint = true }

    fun HTML.renderViewPage(
        root: String,
        index: String,
        id: String,
        doc: JsonObject?,
    ) {
        adminLayout("Dokumentansicht", root) {
            h2 { +"Dokument: $id" }
            div(classes = "doc-meta") {
                +"Index: "
                code { +index }
            }

            if (doc != null) {
                pre {
                    code {
                        +jsonConfig.encodeToString(JsonObject.serializer(), doc)
                    }
                }
            } else {
                div(classes = "msg err") { +"Dokument nicht gefunden." }
            }

            div {
                a(href = "javascript:history.back()", classes = "btn-retry") {
                    attributes["role"] = "button"
                    +"Zurück"
                }
            }
        }
    }

    fun HTML.renderMetaPage(
        root: String,
        entries: List<IngridMetaEntry>,
        message: String?,
        error: String?,
        metaIndexName: String = "ingrid_meta",
    ) {
        adminLayout("Meta-Verwaltung", root, activeTab = "meta") {
            h2 { +"Meta-Verwaltung ($metaIndexName)" }
            p {
                +"Hier sind alle Dokumente des speziellen Index "
                code { +metaIndexName }
                +" gelistet. Diese Dokumente steuern, welche Indizes in der API aktiv sind."
            }

            if (!message.isNullOrBlank()) {
                div(classes = "msg ok") { +message }
            }
            if (!error.isNullOrBlank()) {
                div(classes = "msg err") { +error }
            }

            article(classes = "compact-list") {
                entries
                    .sortedBy { (it.dataSourceName ?: it.indexId ?: it.linkedIndex ?: "").lowercase() }
                    .forEach { entry ->
                        div(classes = "compact-row") {
                            div(classes = "compact-name") {
                                val displayName =
                                    entry.dataSourceName ?: entry.indexId ?: entry.linkedIndex ?: entry.docId
                                a(href = "$root/admin/search/view?index=$metaIndexName&id=${entry.docId}") {
                                    strong { +displayName }
                                }
                                br {}
                                small {
                                    code { +entry.docId }
                                    if (entry.linkedIndex != null) {
                                        +" | Index: "
                                        code { +entry.linkedIndex }
                                    }
                                }
                            }
                            div(classes = "compact-count") {
                                +(if (entry.active) "Aktiv" else "Inaktiv")
                            }
                            div(classes = "delete") {
                                form(action = "$root/admin/meta/${entry.docId}/delete", method = FormMethod.post) {
                                    onClick = "return confirm('Dokument \\'${entry.docId}\\' wirklich löschen?');"
                                    button(type = ButtonType.submit, classes = "btn-delete") { +"Löschen" }
                                }
                            }
                        }
                    }
            }
        }
    }
    private fun FlowContent.renderManagedCards(
        entries: List<IngridMetaEntry>,
        counts: Map<String, Long>,
        root: String,
    ) {
        div(classes = "cards") {
            entries
                .sortedBy { entry ->
                    (entry.dataSourceName ?: entry.indexId ?: entry.linkedIndex ?: "").lowercase()
                }.forEach { entry ->
                    val idx = entry.linkedIndex!!
                    renderManagedCard(idx, entry, counts[idx], root)
                }
        }
    }
}
