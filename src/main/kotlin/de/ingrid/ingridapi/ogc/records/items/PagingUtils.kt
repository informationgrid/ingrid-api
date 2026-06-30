package de.ingrid.ingridapi.ogc.records.items

import de.ingrid.ingridapi.ogc.records.Link
import io.ktor.http.URLBuilder
import io.ktor.http.fullPath
import io.ktor.http.takeFrom
import kotlin.math.max

fun parseBboxParam(bbox: String?): List<Double>? {
    if (bbox == null) return null
    val parts = bbox.split(",")
    if (parts.size != 4 && parts.size != 6) return null
    return try {
        parts.map { it.toDouble() }
    } catch (e: NumberFormatException) {
        null
    }
}

fun createPagingLinks(
    baseUrl: String,
    total: Long,
    limit: Int,
    offset: Int,
    format: String? = null,
    bbox: String? = null,
): List<Link> {
    val links = mutableListOf<Link>()

    fun getContentType(fmt: String?): String =
        when (fmt?.lowercase()) {
            "html" -> "text/html"
            "json", "geojson" -> "application/geo+json"
            "index" -> "application/json"
            "xml", "iso" -> "application/xml"
            "ingrid-index-json" -> "application/vnd.ingrid.index+json"
            "geodcat-xml" -> "application/rdf+xml"
            else -> "application/json"
        }

    val contentType = getContentType(format)

    fun buildHref(newOffset: Int): String {
        val builder = URLBuilder().takeFrom(baseUrl)
        builder.parameters.apply {
            set("limit", limit.toString())
            set("offset", newOffset.toString())
            if (format != null) set("f", format)
            if (bbox != null) set("bbox", bbox)
        }
        val built = builder.build()
        return if (baseUrl.startsWith("http") || baseUrl.startsWith("//")) {
            built.toString()
        } else {
            built.fullPath
        }
    }

    // Next link
    if (offset + limit < total) {
        links.add(
            Link(
                rel = "next",
                href = buildHref(offset + limit),
                type = contentType,
                title = "Next page",
            ),
        )
    }

    // Prev link
    if (offset > 0) {
        links.add(
            Link(
                rel = "prev",
                href = buildHref(max(0, offset - limit)),
                type = contentType,
                title = "Previous page",
            ),
        )
    }

    // First link
    links.add(
        Link(
            rel = "first",
            href = buildHref(0),
            type = contentType,
            title = "First page",
        ),
    )

    // Last link
    val lastOffset = ((total - 1) / limit) * limit
    if (lastOffset >= 0) {
        links.add(
            Link(
                rel = "last",
                href = buildHref(lastOffset.toInt()),
                type = contentType,
                title = "Last page",
            ),
        )
    }

    return links
}
