package de.ingrid.ingridapi.ogc.records.items

import com.jillesvangurp.ktsearch.SearchResponse
import de.ingrid.ingridapi.core.services.asSafeString
import de.ingrid.ingridapi.ogc.records.FeatureCollection
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonObject
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class IsoItemsExporter : ItemsExporter {
    private fun transformIdfToIso(idf: String): String {
        val transformerFactory = TransformerFactory.newInstance()
        val xslStream = this::class.java.classLoader.getResourceAsStream("idf_1_0_0_to_iso_metadata.xsl")
        val transformer = transformerFactory.newTransformer(StreamSource(xslStream))

        val reader = StringReader(idf)
        val writer = StringWriter()
        transformer.transform(StreamSource(reader), StreamResult(writer))
        return writer.toString()
    }

    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        // Convert each hit's idf to ISO and return an aggregated XML document
        val transformed =
            searchResponse
                ?.hits
                ?.hits
                ?.mapNotNull { hit ->
                    val idf = hit.source?.get("idf").asSafeString()
                    if (idf.isBlank()) return@mapNotNull null
                    try {
                        transformIdfToIso(idf)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()

        val xml =
            buildString {
                append("<isoCollection>")
                transformed.forEach { append(it) }
                append("</isoCollection>")
            }
        call.respondText(xml, ContentType.Application.Xml)
    }

    override suspend fun respondSingle(
        call: ApplicationCall,
        record: JsonObject?,
        catalogId: String,
        recordId: String,
    ) {
        if (record == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val idf = record["idf"].asSafeString()
        if (idf.isBlank()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        try {
            val resultXml = transformIdfToIso(idf)
            call.respondText(resultXml, ContentType.Application.Xml)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error during transformation: ${e.message}")
        }
    }
}
