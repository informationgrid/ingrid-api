package de.ingrid.ingridapi.admin

import de.ingrid.ingridapi.admin.ui.AdminPages.renderErrorPage
import de.ingrid.ingridapi.admin.ui.AdminPages.renderIndicesPage
import de.ingrid.ingridapi.admin.ui.AdminPages.renderMetaPage
import de.ingrid.ingridapi.admin.ui.AdminPages.renderSearchPage
import de.ingrid.ingridapi.admin.ui.AdminPages.renderViewPage
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Admin GUI to manage Elasticsearch indices.
 *
 * Provides a small HTML interface (rendered via kotlinx.html DSL) at `/admin`.
 * - Indices referenced from the `ingrid_meta` index are shown prominently as cards.
 * - All other indices are listed compactly below.
 * - Every index can be deleted; managed ones can additionally be enabled/disabled.
 */
fun Application.configureAdminRouting() {
    val root =
        environment.config
            .propertyOrNull("ktor.deployment.rootPath")
            ?.getString()
            ?.trimEnd('/') ?: ""
    routing {
        get("admin/error") {
            val error = call.request.queryParameters["err"]
            call.respondHtml(HttpStatusCode.Forbidden) {
                renderErrorPage(root, error)
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
                        renderIndicesPage(
                            root,
                            managedEntries,
                            others,
                            counts,
                            message,
                            error,
                            elastic.indexPrefix,
                            elastic.metaIndexName
                        )
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

                get("meta") {
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    val entries = runCatchingOrEmptyList { elastic.getMetaEntries() }
                    val message = call.request.queryParameters["msg"]
                    val error = call.request.queryParameters["err"]

                    call.respondHtml(HttpStatusCode.OK) {
                        renderMetaPage(root, entries, message, error, elastic.metaIndexName)
                    }
                }

                post("meta/{docId}/delete") {
                    val docId = call.parameters["docId"].orEmpty()
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    try {
                        elastic.deleteDocument(elastic.metaIndexName, docId)
                        call.respondRedirect("$root/admin/meta?msg=${urlEncode("Dokument '$docId' wurde gelöscht.")}")
                    } catch (ex: Exception) {
                        call.respondRedirect(
                            "$root/admin/meta?err=${urlEncode(
                                "Dokument '$docId' konnte nicht gelöscht werden: ${ex.message}",
                            )}",
                        )
                    }
                }

                get("search") {
                    val q = call.request.queryParameters["q"]
                    val page =
                        call.request.queryParameters["page"]
                            ?.toIntOrNull()
                            ?.coerceAtLeast(1) ?: 1
                    val pageSize = 10
                    val from = (page - 1) * pageSize
                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()

                    val query =
                        buildJsonObject {
                            put("from", JsonPrimitive(from))
                            put("size", JsonPrimitive(pageSize))
                            if (!q.isNullOrBlank()) {
                                put(
                                    "query",
                                    buildJsonObject {
                                        put(
                                            "multi_match",
                                            buildJsonObject {
                                                put("query", JsonPrimitive(q))
                                                put("fields", buildJsonArray { add(JsonPrimitive("*")) })
                                            },
                                        )
                                    },
                                )
                            } else {
                                put(
                                    "query",
                                    buildJsonObject {
                                        put("match_all", buildJsonObject {})
                                    },
                                )
                            }
                        }.toString()

                    val results = runCatching { elastic.search(query) }.getOrNull()

                    call.respondHtml(HttpStatusCode.OK) {
                        renderSearchPage(root, q, results, page, pageSize)
                    }
                }

                get("search/view") {
                    val index = call.request.queryParameters["index"]
                    val id = call.request.queryParameters["id"]

                    if (index == null || id == null) {
                        call.respondRedirect("$root/admin/search")
                        return@get
                    }

                    val elastic = call.application.dependencies.resolve<ElasticsearchService>()
                    val doc = elastic.getDocument(index, id)

                    call.respondHtml(HttpStatusCode.OK) {
                        renderViewPage(root, index, id, doc)
                    }
                }
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
