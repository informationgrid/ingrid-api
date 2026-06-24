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
        val model = createModelWithNamespaces()

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

        /*val created = record["created"]?.asSafeString() ?: ""
        if (created.isNotEmpty()) {
            resource.addProperty(DCTerms.issued, created)
        }*/

        val modified = record["modified"]?.asSafeString() ?: ""
        if (modified.isNotEmpty()) {
            resource.addProperty(DCTerms.modified, modified)
        }

        // Add catalog
        val catalogUri = "$rootPath/ogc/records/collections/$catalogId"
        val catalogResource = model.createResource("http://localhost$catalogUri")
        catalogResource.addProperty(RDF.type, DCAT.Catalog)
        val dcatCatalog = model.createProperty("http://www.w3.org/ns/dcat#catalog")
        resource.addProperty(dcatCatalog, catalogResource)

        // Add distribution
        val distributionUri = "${itemUri}_distribution"
        val distribution = model.createResource(distributionUri)
        distribution.addProperty(RDF.type, DCAT.Distribution)
        val dcatDistribution = model.createProperty("http://www.w3.org/ns/dcat#distribution")
        resource.addProperty(dcatDistribution, distribution)

        // Add access URL to distribution
        val dcatAccessUrl = model.createProperty("http://www.w3.org/ns/dcat#accessURL")
        distribution.addProperty(dcatAccessUrl, "http://localhost$itemUri")

        // Add download URL if available in record
        val downloadUrl = record["url"]?.asSafeString() ?: ""
        if (downloadUrl.isNotEmpty()) {
            val dcatDownloadUrl = model.createProperty("http://www.w3.org/ns/dcat#downloadURL")
            distribution.addProperty(dcatDownloadUrl, downloadUrl)
        }

        // Add publisher
        val publisherName = record["t02_address.address_value"]?.asSafeString() ?: ""
        if (publisherName.isNotEmpty()) {
            val publisherUri = "${itemUri}_publisher"
            val publisher = model.createResource(publisherUri)
            val dcatPublisher = model.createProperty("http://www.w3.org/ns/dcat#publisher")
            val foafAgent = model.createProperty("http://xmlns.com/foaf/0.1/Agent")
            val foafName = model.createProperty("http://xmlns.com/foaf/0.1/name")
            publisher.addProperty(RDF.type, foafAgent)
            publisher.addProperty(foafName, publisherName)
            resource.addProperty(dcatPublisher, publisher)
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

        val mainModel = createModelWithNamespaces()

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

    private fun createModelWithNamespaces(): Model =
        ModelFactory.createDefaultModel().apply {
            setNsPrefix("dct", DCTerms.getURI())
            setNsPrefix("dcat", DCAT.getURI())
            setNsPrefix("rdf", RDF.getURI())
            setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
            setNsPrefix("geodcat", "http://data.europa.eu/930/def/geodcat-ap#")
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
