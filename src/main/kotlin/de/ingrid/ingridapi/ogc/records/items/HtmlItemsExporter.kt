package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.total
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
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
        val html =
            buildString {
                append(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8"/>
                      <title>Items of ${escapeHtml(featureCollection.name)}</title>
                      <style>
                        body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif; margin:20px}
                        table{border-collapse:collapse; width:100%}
                        th,td{border:1px solid #ddd; padding:8px}
                        th{background:#f5f5f5; text-align:left}
                        caption{font-weight:600; margin-bottom:8px}
                        code{background:#f6f8fa; padding:2px 4px; border-radius:4px}
                        .paging{margin-top:20px; display:flex; gap:10px; align-items:center}
                        .paging a{padding:5px 10px; border:1px solid #ddd; text-decoration:none; color:#333; border-radius:4px}
                        .paging a:hover{background:#f5f5f5}
                        .paging .current{font-weight:bold}
                        ul{list-style:none; padding:0}
                        li{margin-bottom:8px}
                        .link-rel{font-weight:bold; display:inline-block; width:80px}
                      </style>
                    </head>
                    <body>
                      <nav>
                        <a href="$root/ogc/records">Home</a> / 
                        <a href="$root/ogc/records/collections?f=html">Collections</a> / 
                        <a href="$root/ogc/records/collections/${escapeHtml(featureCollection.name)}?f=html">${
                        escapeHtml(
                            featureCollection.name,
                        )
                    }</a>
                      </nav>
                      <h1>Items of ${escapeHtml(featureCollection.name)}</h1>
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

                // Paging UI in the body
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

                append("<h2>Links</h2><ul>")
                for (link in featureCollection.links) {
                    append("<li>")
                    append("<span class=\"link-rel\">").append(escapeHtml(link.rel)).append("</span>")
                    append("<a href=\"").append(escapeHtml(link.href)).append("\">")
                    append(escapeHtml(link.title ?: link.href))
                    append("</a>")
                    if (link.type != null) {
                        append(" (<code>").append(escapeHtml(link.type)).append("</code>)")
                    }
                    append("</li>")
                }
                append("</ul>")

                append("</body></html>")
            }
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

        /*val contactList = mutableListOf<String>()
        val addressElement = record["address"]
        if (addressElement is JsonArray) {
            addressElement.forEach {
                if (it is JsonObject) {
                    val c = formatContact(it)
                    if (c.isNotEmpty()) contactList.add(c)
                }
            }
        } else {
            val c = formatContact(record)
            if (c.isNotEmpty()) contactList.add(c)
        }*/

        val html =
            buildString {
                append(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8"/>
                      <title>Record: ${escapeHtml(title)}</title>
                      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                      <style>
                        body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif; margin:20px}
                        th,td{padding:8px; text-align:left; vertical-align:top}
                        th{background:#f5f5f5; width:200px; border:1px solid #ddd}
                        td{border:1px solid #ddd}
                        table{border-collapse:collapse; width:100%; margin-bottom:20px}
                        ul{list-style:none; padding:0}
                        li{margin-bottom:8px}
                        .link-rel{font-weight:bold; display:inline-block; width:80px}
                        code{background:#f6f8fa; padding:2px 4px; border-radius:4px}
                        #map { height: 400px; width: 100%; margin-top: 20px; border: 1px solid #ddd; border-radius: 4px; }
                      </style>
                    </head>
                    <body>
                    <nav>
                      <a href="$root/ogc/records">Home</a> / 
                      <a href="$root/ogc/records/collections?f=html">Collections</a> / 
                      <a href="$root/ogc/records/collections/${escapeHtml(catalogId)}?f=html">${escapeHtml(catalogId)}</a>
                    </nav>
                      <h1>Record: ${escapeHtml(title)}</h1>
                      
                      <table>
                        <tr><th>UUID</th><td>${escapeHtml(uuid)}</td></tr>
                        <tr><th>Created</th><td>${escapeHtml(created)}</td></tr>
                        <tr><th>Modified</th><td>${escapeHtml(modified)}</td></tr>
                        <tr><th>Title</th><td>${escapeHtml(title)}</td></tr>
                        <tr><th>Description</th><td>${escapeHtml(description)}</td></tr>
                        <!--<tr><th>Contacts</th><td></td></tr>-->
                      </table>

                      <div id="map"></div>
                      <script>
                        var geometry = ${
                        ((record["spatial"] as JsonObject?)?.get("geometries") as JsonArray?)?.get(0)
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

                      <h2>Links</h2>
                      <ul>
                        <li>
                          <span class="link-rel">self</span>
                          <a href="$root/ogc/records/collections/${
                        escapeHtml(
                            catalogId,
                        )
                    }/items/${escapeHtml(recordId)}?f=html">This record as HTML</a>
                          (<code>text/html</code>)
                        </li>
                        <li>
                          <span class="link-rel">alternate</span>
                          <a href="$root/ogc/records/collections/${
                        escapeHtml(
                            catalogId,
                        )
                    }/items/${escapeHtml(recordId)}?f=geojson">This record as GeoJSON</a>
                          (<code>application/geo+json</code>)
                        </li>
                        <li>
                          <span class="link-rel">alternate</span>
                          <a href="$root/ogc/records/collections/${
                        escapeHtml(
                            catalogId,
                        )
                    }/items/${escapeHtml(recordId)}?f=iso">This record as ISO XML</a>
                          (<code>application/xml</code>)
                        </li>
                        <li>
                          <span class="link-rel">alternate</span>
                          <a href="$root/ogc/records/collections/${
                        escapeHtml(
                            catalogId,
                        )
                    }/items/${escapeHtml(recordId)}?f=ingrid-index-json">This record as INGRID INDEX JSON</a>
                          (<code>application/vnd.ingrid.index+json</code>)
                        </li>
                        <li>
                          <span class="link-rel">alternate</span>
                          <a href="$root/ogc/records/collections/${
                        escapeHtml(
                            catalogId,
                        )
                    }/items/${escapeHtml(recordId)}?f=geodcat">This record as GeoDCAT RDF/XML</a>
                          (<code>application/rdf+xml</code>)
                        </li>
                        <li>
                          <span class="link-rel">collection</span>
                          <a href="$root/ogc/records/collections/${escapeHtml(catalogId)}?f=html">The collection description</a>
                          (<code>text/html</code>)
                        </li>
                      </ul>
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

    private fun formatContact(obj: JsonObject): String {
        val organisation = obj["organisation"].asSafeString()
        val firstName = obj["t02_address.firstname"].asSafeString()
        val lastName = obj["t02_address.lastname"].asSafeString()
        val person = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(organisation, person).filter { it.isNotEmpty() }.joinToString(", ")
    }
}
