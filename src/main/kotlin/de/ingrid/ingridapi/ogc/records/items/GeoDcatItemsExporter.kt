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
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.DCAT
import org.apache.jena.vocabulary.DCTerms
import org.apache.jena.vocabulary.RDF
import java.io.StringWriter

/**
 * Exporter for GeoDCAT (RDF/XML) format (application/rdf+xml)
 */
class GeoDcatItemsExporter : ItemsExporter {

    private fun createGeoDcatModel(
        record: JsonObject,
        catalogId: String,
        recordId: String,
        rootPath: String,
    ): Model {
        val model = ModelFactory.createDefaultModel()
        model.setNsPrefix("dct", DCTerms.getURI())
        model.setNsPrefix("dcat", DCAT.getURI())
        model.setNsPrefix("rdf", RDF.getURI())
        model.setNsPrefix("geodcat", "http://data.europa.eu/930/def/geodcat-ap#")

        val itemUri = "$rootPath/ogc/records/collections/$catalogId/items/$recordId"
        val resource = model.createResource("http://localhost$itemUri")
        resource.addProperty(RDF.type, DCAT.Dataset)

        val title = record["title"].asSafeString().ifEmpty { recordId }
        resource.addProperty(DCTerms.title, title)

        val description = record["description"]?.asSafeString() ?: record["summary"].asSafeString()
        if (description.isNotEmpty()) {
            resource.addProperty(DCTerms.description, description)
        }

        val uuid =
            record["obj_uuid"]?.asSafeString()?.ifEmpty { record["addr_uuid"]?.asSafeString() }?.ifEmpty { recordId }
                ?: recordId
        resource.addProperty(DCTerms.identifier, uuid)

        val created = record["created"]?.asSafeString() ?: ""
        if (created.isNotEmpty()) {
            resource.addProperty(DCTerms.issued, created)
        }

        val modified = record["modified"]?.asSafeString() ?: ""
        if (modified.isNotEmpty()) {
            resource.addProperty(DCTerms.modified, modified)
        }

        return model
    }

    override suspend fun respond(
        call: ApplicationCall,
        featureCollection: FeatureCollection,
        searchResponse: SearchResponse?,
        limit: Int,
        offset: Int,
        bbox: String?,
    ) {
        val rootPath =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""
        val catalogId = featureCollection.name

        val mainModel = ModelFactory.createDefaultModel()
        mainModel.setNsPrefix("dct", DCTerms.getURI())
        mainModel.setNsPrefix("dcat", DCAT.getURI())
        mainModel.setNsPrefix("rdf", RDF.getURI())
        mainModel.setNsPrefix("geodcat", "http://data.europa.eu/930/def/geodcat-ap#")

        searchResponse?.hits?.hits?.forEach { hit ->
            val record = hit.source ?: return@forEach
            val recordId = hit.id
            val itemModel = createGeoDcatModel(record, catalogId, recordId, rootPath)
            mainModel.add(itemModel)
        }

        val writer = StringWriter()
        mainModel.write(writer, "RDF/XML-ABBREV", "http://localhost")
        call.respondText(writer.toString(), ContentType.parse("application/rdf+xml"))
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

        val rootPath =
            call.application.environment.config
                .propertyOrNull("ktor.deployment.rootPath")
                ?.getString()
                ?.trimEnd('/') ?: ""

        try {
            val model = createGeoDcatModel(record, catalogId, recordId, rootPath)
            val writer = StringWriter()
            model.write(writer, "RDF/XML-ABBREV", "http://localhost")
            call.respondText(writer.toString(), ContentType.parse("application/rdf+xml"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error generating GeoDCAT format: ${e.message}")
        }
    }
}
