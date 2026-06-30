package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.total
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import de.ingrid.ingridapi.ogc.records.Link
import de.ingrid.ingridapi.ogc.records.export.HtmlTemplateUtils
import de.ingrid.ingridapi.ogc.records.export.HtmlTemplateUtils.escapeHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.min

class HtmlItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        val root =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        val total = searchResponse?.total ?: 0L
        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <table>
                        <caption>Showing ${offset + 1} - ${
                        min(
                            offset + limit.toLong(),
                            total,
                        )
                    } of $total items</caption>
                        <thead>
                          <tr><th>Title</th><th>Description</th></tr>
                        </thead>
                        <tbody>
                    """.trimIndent(),
                )
                searchResponse?.hits?.hits?.forEach {
                    val id = it.id
                    val title = it.source!!["title"].asSafeString().ifEmpty { id }
                    val description =
                        it.source?.get("description")?.asSafeString() ?: it.source?.get("summary").asSafeString()
                    append("<tr>")
                    append("<td><a href=\"$root/ogc/records/collections/")
                        .append(escapeHtml(featureCollection.name))
                        .append("/items/")
                        .append(escapeHtml(id))
                        .append("?f=html\">")
                        .append(escapeHtml(title))
                        .append("</a></td>")
                    append("<td>").append(escapeHtml(description)).append("</td>")
                    append("</tr>")
                }
                append("</tbody></table>")

                val selfLink = featureCollection.links.find { it.rel == "self" }
                val baseUrl = selfLink?.href ?: ""
                val pagingLinks = createPagingLinks(baseUrl, total, limit, offset, "html", bbox)

                if (total > limit) {
                    append("<div class=\"paging\">")
                    pagingLinks.find { it.rel == "prev" }?.let {
                        append("<a href=\"${it.href}\">&laquo; Previous</a>")
                    }
                    val currentPage = (offset / limit) + 1
                    val totalPages = (total + limit - 1) / limit
                    append("<span class=\"current\">Page $currentPage of $totalPages</span>")
                    pagingLinks.find { it.rel == "next" }?.let {
                        append("<a href=\"${it.href}\">Next &raquo;</a>")
                    }
                    append("</div>")
                }
                append("</div>")

                append(HtmlTemplateUtils.renderLinksSection(featureCollection.links))
            }
        val breadcrumbs =
            listOf(
                "Home" to "$root/ogc/records",
                "Collections" to "$root/ogc/records/collections?f=html",
                featureCollection.name to "$root/ogc/records/collections/${featureCollection.name}?f=html",
            )
        val html = HtmlTemplateUtils.renderHtmlPage("Items of ${featureCollection.name}", breadcrumbs, content)
        call.respondText(html, ContentType.Text.Html)
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    ) {
        val root =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        if (record == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val title = record["title"].asSafeString()
        val description = record["description"]?.asSafeString() ?: record["summary"]?.asSafeString() ?: ""
        val uuid =
            record["obj_uuid"]?.asSafeString()?.ifEmpty { record["addr_uuid"]?.asSafeString() }?.ifEmpty { recordId }
                ?: recordId
        val created = record["created"]?.asSafeString() ?: ""
        val modified = record["modified"]?.asSafeString() ?: ""

        val content =
            buildString {
                append(
                    """
                    <div class="card">
                      <table>
                        <tr><th>UUID</th><td>${escapeHtml(uuid)}</td></tr>
                        <tr><th>Created</th><td>${escapeHtml(created)}</td></tr>
                        <tr><th>Modified</th><td>${escapeHtml(modified)}</td></tr>
                        <tr><th>Title</th><td>${escapeHtml(title)}</td></tr>
                        <tr><th>Description</th><td>${escapeHtml(description)}</td></tr>
                      </table>
                    </div>
    
                    <div id="map"></div>
                    <script>
                      var geometry = ${
                        ((record["spatial"] as JsonObject?)?.get("geometries") as JsonArray?)
                            ?.getOrNull(0)
                            ?.toString() ?: "null"
                    };
                      if (geometry) {
                          var map = L.map('map');
                          L.tileLayer('https://{s}.tile.openstreetmap.de/{z}/{x}/{y}.png', {
                              attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                          }).addTo(map);
                          var geojsonLayer = L.geoJSON(geometry).addTo(map);
                          map.fitBounds(geojsonLayer.getBounds());
                      } else {
                          document.getElementById('map').style.display = 'none';
                      }
                    </script>
                    """.trimIndent(),
                )

                val links =
                    listOf(
                        Link("self", "$root/ogc/records/collections/$catalogId/items/$recordId?f=html", "text/html", "This record as HTML"),
                        Link("alternate", "$root/ogc/records/collections/$catalogId/items/$recordId?f=geojson", "application/geo+json", "This record as GeoJSON"),
                        Link("alternate", "$root/ogc/records/collections/$catalogId/items/$recordId?f=iso", "application/xml", "This record as ISO XML"),
                        Link("alternate", "$root/ogc/records/collections/$catalogId/items/$recordId?f=ingrid-index-json", "application/vnd.ingrid.index+json", "This record as INGRID INDEX JSON"),
                        Link("alternate", "$root/ogc/records/collections/$catalogId/items/$recordId?f=geodcat", "application/rdf+xml", "This record as GeoDCAT RDF/XML"),
                        Link("collection", "$root/ogc/records/collections/$catalogId?f=html", "text/html", "The collection description"),
                    )
                append(HtmlTemplateUtils.renderLinksSection(links))
            }
        val breadcrumbs =
            listOf(
                "Home" to "$root/ogc/records",
                "Collections" to "$root/ogc/records/collections?f=html",
                catalogId to "$root/ogc/records/collections/$catalogId?f=html",
                "Items" to "$root/ogc/records/collections/$catalogId/items?f=html",
            )

        val headExtra =
            """
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            """.trimIndent()

        val html = HtmlTemplateUtils.renderHtmlPage("Record: $title", breadcrumbs, content, headExtra)
        call.respondText(html, ContentType.Text.Html)
    }

    private fun formatContact(obj: JsonObject): String {
        val organisation = obj["organisation"].asSafeString()
        val firstName = obj["t02_address.firstname"].asSafeString()
        val lastName = obj["t02_address.lastname"].asSafeString()
        val person = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(organisation, person).filter { it.isNotEmpty() }.joinToString(", ")
    }
}
