package de.ingrid.ingridapi.ogc.records.items

import org.junit.Test
import kotlin.test.assertFalse

class IsoItemsExporterTest {

    @Test
    fun testTransformationDoesNotIncludeXmlDeclaration() {
        val idf = """
            <idf:idfMdMetadata xmlns:idf="http://www.portalu.de/IDF/1.0" uuid="test-uuid" id="test-id">
                <idf:idfResponsibleParty>
                    <idf:individualName>John Doe</idf:individualName>
                </idf:idfResponsibleParty>
            </idf:idfMdMetadata>
        """.trimIndent()

        val exporter = IsoItemsExporter()
        val result = exporter.transformIdfToIso(idf)

        println("Transformation result:\n$result")
        
        // The issue states it contains <?xml version ... which must be removed
        assertFalse(result.contains("<?xml"), "Result should NOT contain XML declaration")
    }
}
