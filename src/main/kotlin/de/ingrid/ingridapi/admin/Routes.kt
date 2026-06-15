package de.ingrid.ingridapi.admin

import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.IngridMetaEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.meta
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.styleLink
import kotlinx.html.title
import kotlinx.html.unsafe

/**
 * Admin GUI to manage Elasticsearch indices.
 *
 * Provides a small HTML interface (rendered via kotlinx.html DSL) at `/admin`.
 * - Indices referenced from the `ingrid_meta` index are shown prominently as cards.
 * - All other indices are listed compactly below.
 * - Every index can be deleted; managed ones can additionally be enabled/disabled.
 */
fun Application.configureAdminRouting() {
    val root = environment.config.propertyOrNull("ktor.deployment.rootPath")?.getString()?.trimEnd('/') ?: ""
    routing {
        get("admin/error") {
            val error = call.request.queryParameters["err"]
            call.respondHtml(HttpStatusCode.Forbidden) {
                head {
                    meta(charset = "utf-8")
                    title("InGrid API – Administration (Fehler)")
                    styleLink("https://cdn.jsdelivr.net/npm/water.css@2/out/water.min.css")
                    style {
                        unsafe { +CSS }
                    }
                }
                body {
                    h1 { +"InGrid API – Administration" }
                    if (!error.isNullOrBlank()) {
                        div(classes = "msg err") { +error }
                    }
                    p {
                        +"Sie haben keine Berechtigung für diesen Bereich oder ein Sitzungsfehler ist aufgetreten."
                    }
                    div {
                        a(href = "$root/auth/login", classes = "btn-retry") {
                            +"Erneut versuchen"
                        }
                    }
                }
            }
        }

        authenticate("admin-session") {
            route("admin") {
                get {
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    val indices = runCatchingOrEmptyMap { elastic.listIndicesWithAliases() }
                    val metaEntries = runCatchingOrEmptyList { elastic.getMetaEntries() }
                    val message = call.request.queryParameters["msg"]
                    val error = call.request.queryParameters["err"]

                    // Pre-fetch document counts (kotlinx.html DSL is non-suspending).
                    val counts: Map<String, Long> =
                        indices.keys.associateWith { elastic.countDocuments(it) }

                    // Each ingrid_meta entry is shown separately (do NOT group by index/alias).
                    val managedEntries: List<IngridMetaEntry> =
                        metaEntries.filter { !it.linkedIndex.isNullOrBlank() && it.linkedIndex in indices }

                    val managedIndexNames = managedEntries.mapNotNull { it.linkedIndex }.toSet()
                    val others = indices.filterKeys { it !in managedIndexNames }

                    call.respondHtml(HttpStatusCode.OK) {
                        head {
                            meta(charset = "utf-8")
                            title("InGrid API – Administration")
                            styleLink("https://cdn.jsdelivr.net/npm/water.css@2/out/water.min.css")
                            style {
                                unsafe { +CSS }
                            }
                        }
                        body {
                            h1 { +"InGrid API – Administration" }
                            p {
                                +"Verwaltung der Elasticsearch-Indizes. Indizes, die in "
                                code { +"ingrid_meta" }
                                +" referenziert sind, werden als Karten hervorgehoben."
                            }
                            if (!message.isNullOrBlank()) {
                                div(classes = "msg ok") { +message }
                            }
                            if (!error.isNullOrBlank()) {
                                div(classes = "msg err") { +error }
                            }

                            h2 { +"Verwaltete Indizes" }
                            if (managedEntries.isEmpty()) {
                                p { +"Keine Indizes in 'ingrid_meta' referenziert." }
                            } else {
                                div(classes = "cards") {
                                    managedEntries
                                        .sortedBy { entry ->
                                            (entry.dataSourceName ?: entry.indexId ?: entry.linkedIndex ?: "").lowercase()
                                        }.forEach { entry ->
                                            val idx = entry.linkedIndex!!
                                            renderManagedCard(idx, entry, counts[idx], root)
                                        }
                                }
                            }

                            h2 { +"Weitere Indizes" }
                            if (others.isEmpty()) {
                                p { +"Keine weiteren Indizes vorhanden." }
                            } else {
                                div(classes = "compact-list") {
                                    others.entries.sortedBy { it.key }.forEach { (index, _) ->
                                        renderCompactRow(index, counts[index], root)
                                    }
                                }
                            }
                        }
                    }
                }

                post("indices/{name}/delete") {
                    val name = call.parameters["name"].orEmpty()
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    try {
                        elastic.deleteIndex(name)
                        call.respondRedirect("$root/admin?msg=${urlEncode("Index '$name' wurde gelöscht.")}")
                    } catch (ex: Exception) {
                        call.respondRedirect(
                            "$root/admin?err=${urlEncode("Index '$name' konnte nicht gelöscht werden: ${ex.message}")}",
                        )
                    }
                }

                post("meta/{docId}/active") {
                    val docId = call.parameters["docId"].orEmpty()
                    val params = call.receiveParameters()
                    val active = params["active"]?.toBooleanStrictOrNull() ?: false
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    try {
                        // dataSourceName VOR dem Update auflösen, damit wir ihn in der Nachricht
                        // anzeigen können (statt der internen ID des ingrid_meta-Dokuments).
                        val displayName =
                            runCatching { elastic.getMetaEntries() }
                                .getOrNull()
                                ?.firstOrNull { it.docId == docId }
                                ?.let { it.dataSourceName ?: it.indexId ?: it.linkedIndex }
                                ?: docId
                        elastic.setMetaActive(docId, active)
                        val state = if (active) "aktiviert" else "deaktiviert"
                        call.respondRedirect(
                            "$root/admin?msg=${urlEncode("'$displayName' wurde $state.")}",
                        )
                    } catch (ex: Exception) {
                        call.respondRedirect(
                            "$root/admin?err=${urlEncode("'$docId' konnte nicht aktualisiert werden: ${ex.message}")}",
                        )
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderManagedCard(
    index: String,
    entry: IngridMetaEntry,
    docCount: Long?,
    root: String,
) {
    val displayName = entry.dataSourceName ?: entry.indexId ?: index

    div(classes = "card") {
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
            div(classes = "name") { +displayName }
            div(classes = "index-name") { +index }
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
        }

        // RIGHT: Löschen
        div(classes = "delete") {
            form(action = "$root/admin/indices/$index/delete", method = FormMethod.post) {
                onClick = "return confirm('Index \\'$index\\' wirklich löschen? Dies kann nicht rückgängig gemacht werden.');"
                button(type = ButtonType.submit, classes = "btn-delete") { +"Löschen" }
            }
        }
    }
}

private fun FlowContent.renderCompactRow(
    index: String,
    docCount: Long?,
    root: String,
) {
    div(classes = "compact-row") {
        div(classes = "compact-name") { +index }
        div(classes = "compact-count") {
            span(classes = "label") { +"Dokumente: " }
            +(docCount?.toString() ?: "?")
        }
        div(classes = "delete") {
            form(action = "$root/admin/indices/$index/delete", method = FormMethod.post) {
                onClick = "return confirm('Index \\'$index\\' wirklich löschen? Dies kann nicht rückgängig gemacht werden.');"
                button(type = ButtonType.submit, classes = "btn-delete") { +"Löschen" }
            }
        }
    }
}

// --- helpers ---------------------------------------------------------------

private inline fun <K, V> runCatchingOrEmptyMap(block: () -> Map<K, V>): Map<K, V> =
    try {
        block()
    } catch (_: Exception) {
        emptyMap()
    }

private inline fun <T> runCatchingOrEmptyList(block: () -> List<T>): List<T> =
    try {
        block()
    } catch (_: Exception) {
        emptyList()
    }

private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

/**
 * Wandelt den `lastIndexed`-Wert aus dem ingrid_meta-Index in ein lesbares
 * Datum + Uhrzeit um. Der Wert kann ein Millisekunden-Zeitstempel (Long als String)
 * oder ein ISO-8601 Datums-String sein. Bei einem nicht erkennbaren Format wird
 * der Original-Wert zurückgegeben.
 */
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

private val CSS =
    """
    /* Force a light, high-contrast theme so colors stay readable
       regardless of the user's OS dark-mode preference. */
    body { background: #f7f8fa !important; color: #1a1a1a !important; }
    h1, h2, h3, p, div, span, code { color: #1a1a1a; }
    code { background: #eef1f5; padding: 1px 5px; border-radius: 3px; color: #222; }

    .msg { padding: 10px 14px; border-radius: 4px; margin: 10px 0; font-weight: 500; }
    .msg.ok  { background: #e6f4ea; color: #14532d; border: 1px solid #34a853; }
    .msg.err { background: #fdecea; color: #7f1d1d; border: 1px solid #d93025; }

    .cards { display: flex; flex-direction: column; gap: 12px; margin-bottom: 28px; }
    .card {
        display: flex;
        align-items: center;
        gap: 18px;
        padding: 14px 18px;
        background: #ffffff;
        border: 1px solid #d6dae0;
        border-left: 5px solid #1565c0;
        border-radius: 6px;
        box-shadow: 0 1px 2px rgba(0,0,0,0.04);
        color: #1a1a1a;
    }
    .card .toggle { flex: 0 0 auto; }
    .card .info   { flex: 1 1 auto; min-width: 0; }
    .card .delete { flex: 0 0 auto; }
    .card .name {
        font-size: 1.2em;
        font-weight: 700;
        color: #0d2b4e;
    }
    .card .index-name {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.88em;
        color: #4a5568;
        margin-top: 2px;
    }
    .card .meta-line { margin-top: 8px; font-size: 0.92em; color: #1a1a1a; }
    .card .meta-line .metric { margin-right: 20px; }
    .card .meta-line .label  { color: #5a6470; font-weight: 500; }

    button.btn-on, button.btn-off {
        min-width: 72px;
        padding: 7px 14px;
        border-radius: 18px;
        border: 1px solid transparent;
        font-weight: 700;
        cursor: pointer;
        color: #ffffff;
        letter-spacing: 0.5px;
    }
    button.btn-on  { background: #1b873f; border-color: #14672f; }
    button.btn-on:hover  { background: #14672f; }
    button.btn-off { background: #5f6b7a; border-color: #4a5562; }
    button.btn-off:hover { background: #4a5562; }
    button.btn-delete {
        background: #b3261e;
        color: #ffffff;
        border: 1px solid #8c1d18;
        padding: 7px 14px;
        border-radius: 4px;
        font-weight: 600;
        cursor: pointer;
    }
    button.btn-delete:hover { background: #8c1d18; }
    
    a.btn-retry {
        text-decoration: none;
        display: inline-block;
        background-color: #1565c0;
        color: white !important;
        padding: 8px 16px;
        border-radius: 4px;
        font-weight: 600;
    }
    a.btn-retry:hover { background-color: #0d47a1; }

    .compact-list {
        display: flex;
        flex-direction: column;
        background: #ffffff;
        border: 1px solid #d6dae0;
        border-radius: 6px;
        overflow: hidden;
    }
    .compact-row {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 8px 14px;
        border-bottom: 1px solid #e4e7eb;
        color: #1a1a1a;
    }
    .compact-row:last-child { border-bottom: none; }
    .compact-row:nth-child(even) { background: #f4f6f9; }
    .compact-row .compact-name {
        flex: 1 1 auto;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        color: #1a1a1a;
    }
    .compact-row .compact-count { flex: 0 0 auto; font-size: 0.92em; color: #1a1a1a; }
    .compact-row .label { color: #5a6470; font-weight: 500; }
    """.trimIndent()
