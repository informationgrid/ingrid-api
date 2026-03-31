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

    val formatParam = if (format != null) "&format=$format" else ""
    val bboxParam = if (bbox != null) "&bbox=$bbox" else ""

    // Next link
    if (offset + limit < total) {
        val nextOffset = offset + limit
        links.add(
            Link(
                rel = "next",
                href = "$baseUrl?limit=$limit&offset=$nextOffset$formatParam$bboxParam",
                type = if (format == "html") "text/html" else "application/geo+json",
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
                type = if (format == "html") "text/html" else "application/geo+json",
                title = "Previous page",
            ),
        )
    }

    // First link
    links.add(
        Link(
            rel = "first",
            href = "$baseUrl?limit=$limit&offset=0$formatParam$bboxParam",
            type = if (format == "html") "text/html" else "application/geo+json",
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
                type = if (format == "html") "text/html" else "application/geo+json",
                title = "Last page",
            ),
        )
    }

    return links
}
