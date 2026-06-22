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

/**
 * Exporter for GeoDCAT (RDF/XML) format (application/rdf+xml)
 * This format transforms IDF metadata to GeoDCAT RDF/XML representation.
 */
class GeoDcatItemsExporter : ItemsExporter {
    
    private fun transformIdfToGeoDcat(idf: String): String {
        // Try to use XSLT transformation if available
        val transformerFactory = TransformerFactory.newInstance()
        
        // Try to load GeoDCAT transformation XSL
        val xslStream = this::class.java.classLoader.getResourceAsStream("idf_to_geodcat.xsl")
            ?: this::class.java.classLoader.getResourceAsStream("idf_1_0_0_to_geodcat.xsl")
        
        if (xslStream != null) {
            val transformer = transformerFactory.newTransformer(StreamSource(xslStream))
            val reader = StringReader(idf)
            val writer = StringWriter()
            transformer.transform(StreamSource(reader), StreamResult(writer))
            return writer.toString()
        }
        
        // Fallback: Return a basic RDF/XML structure with the IDF content
        // This is a placeholder - in production, a proper XSLT transformation should be used
        return """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:geodcat="http://data.europa.eu/930/def/geodcat-ap#"
         xmlns:dct="http://purl.org/dc/terms/"
         xmlns:dcat="http://www.w3.org/ns/dcat#">
    <geodcat:Dataset rdf:about="">
        <dct:description>${idf.escapeXml()}</dct:description>
    </geodcat:Dataset>
</rdf:RDF>""".trimIndent()
    }
    
    private fun String.escapeXml(): String =
        this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        // Convert each hit's idf to GeoDCAT RDF/XML and return an aggregated document
        val transformed =
            searchResponse
                ?.hits
                ?.hits
                ?.mapNotNull { hit ->
                    val idf = hit.source?.get("idf").asSafeString()
                    if (idf.isBlank()) return@mapNotNull null
                    try {
                        transformIdfToGeoDcat(idf)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()

        val rdfXml =
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
""")
                transformed.forEach { append(it) }
                append("\n</rdf:RDF>")
            }
        call.respondText(rdfXml, ContentType.parse("application/rdf+xml"))
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
            // If no IDF, try to use the record directly as a fallback
            call.respond(HttpStatusCode.NotFound, "No IDF metadata available for GeoDCAT transformation")
            return
        }

        try {
            val resultRdfXml = transformIdfToGeoDcat(idf)
            // Add links to the RDF/XML response
            val responseWithLinks = """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about="/ogc/records/collections/$catalogId/items/$recordId">
        <rdf:type rdf:resource="http://www.w3.org/ns/dcat#Dataset"/>
    </rdf:Description>
    $resultRdfXml
</rdf:RDF>"""
            call.respondText(responseWithLinks, ContentType.parse("application/rdf+xml"))
        } catch (e: Exception) {
            // If transformation fails, the record is not available in this format
            call.respond(HttpStatusCode.NotAcceptable, "Record not available in GeoDCAT format: ${e.message}")
        }
    }
}
