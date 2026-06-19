package de.ingrid.ingridapi.ogc.records.items

import de.ingrid.ingridapi.ogc.records.Link
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

    val formatParam = if (format != null) "&f=$format" else ""
    val bboxParam = if (bbox != null) "&bbox=$bbox" else ""

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

    // Next link
    if (offset + limit < total) {
        val nextOffset = offset + limit
        links.add(
            Link(
                rel = "next",
                href = "$baseUrl?limit=$limit&offset=$nextOffset$formatParam$bboxParam",
                type = contentType,
                title = "Next page",
            ),
        )
    }

    // Prev link
    if (offset > 0) {
        val prevOffset = max(0, offset - limit)
        links.add(
            Link(
                rel = "prev",
                href = "$baseUrl?limit=$limit&offset=$prevOffset$formatParam$bboxParam",
                type = contentType,
                title = "Previous page",
            ),
        )
    }

    // First link
    links.add(
        Link(
            rel = "first",
            href = "$baseUrl?limit=$limit&offset=0$formatParam$bboxParam",
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
                href = "$baseUrl?limit=$limit&offset=$lastOffset$formatParam$bboxParam",
                type = contentType,
                title = "Last page",
            ),
        )
    }

    return links
}
