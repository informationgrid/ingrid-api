package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

class HtmlItemsExporter : ItemsExporter {
    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        records: SearchResponse.Hits?,
    ) {
        val html =
            buildString {
                append("<html><body><h1>Items of ${featureCollection.name}</h1><dl>")
                records?.hits?.forEach {
                    append(
                        $"""
                            <dt style="font-weight: bold;">${it.source!!["title"].asSafeString()}</dt>
                            <dd style="margin-bottom: 1em;">${it.source!!["description"].asSafeString()}</dd>
                        """.trimIndent(),
                    )
                }
                append("</dl></body></html>")
            }
        call.respondText(html, ContentType.Text.Html)
    }
}
