package de.ingrid.ingridapi.ogc.records.items

import de.ingrid.ingridapi.ogc.records.Link
import kotlin.math.max

fun createPagingLinks(
    baseUrl: String,
    total: Long,
    limit: Int,
    offset: Int,
    format: String? = null,
): List<Link> {
    val links = mutableListOf<Link>()

    val formatParam = if (format != null) "&format=$format" else ""

    // Next link
    if (offset + limit < total) {
        val nextOffset = offset + limit
        links.add(
            Link(
                rel = "next",
                href = "$baseUrl?limit=$limit&offset=$nextOffset$formatParam",
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
                href = "$baseUrl?limit=$limit&offset=$prevOffset$formatParam",
                type = if (format == "html") "text/html" else "application/geo+json",
                title = "Previous page",
            ),
        )
    }

    // First link
    links.add(
        Link(
            rel = "first",
            href = "$baseUrl?limit=$limit&offset=0$formatParam",
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
                href = "$baseUrl?limit=$limit&offset=$lastOffset$formatParam",
                type = if (format == "html") "text/html" else "application/geo+json",
                title = "Last page",
            ),
        )
    }

    return links
}
