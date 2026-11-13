package de.ingrid.ingridapi.opensearch.service

/**
 * Renders the OpenSearch Description Document (OSDD) served at `/opensearch/descriptor`.
 *
 * This is the migration of `de.ingrid.iface.opensearch.ProvideDescriptorServlet` together with
 * the `descriptor.xml` resource of ingrid-interface-search. In contrast to the static template
 * of the legacy application, the document is generated dynamically with the base URL of the
 * current request.
 */
class DescriptorRenderer(
    private val shortName: String,
    private val description: String,
    private val tags: String,
    private val contact: String,
) {
    /**
     * Renders the OSDD.
     *
     * @param baseUrl the absolute base URL of the application, e.g. `https://host/ingrid-api`
     */
    fun render(baseUrl: String): String {
        val queryUrl = "${baseUrl.trimEnd('/')}/opensearch/query"
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append(
            """<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/""""
                + """ xmlns:ingridsearch="http://www.informationgrid.eu/opensearch/extension/1.0">""",
        )
        sb.append("<ShortName>${xmlEscape(shortName)}</ShortName>")
        sb.append("<Description>${xmlEscape(description)}</Description>")
        sb.append("<Tags>${xmlEscape(tags)}</Tags>")
        if (contact.isNotEmpty()) {
            sb.append("<Contact>${xmlEscape(contact)}</Contact>")
        }
        sb.append(
            """<Url type="application/rss+xml" template="${xmlEscape(
                "$queryUrl?q={searchTerms}&p={startPage?}&h={count?}&format=rss",
            )}"/>""",
        )
        sb.append("""<Query role="example" searchTerms="boden"/>""")
        sb.append("""<Query role="example" searchTerms="wasser"/>""")
        sb.append("</OpenSearchDescription>")
        return sb.toString()
    }
}
