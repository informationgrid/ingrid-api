package de.ingrid.ingridapi.atom

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Test fixtures for the ATOM Download Service: sample IDF documents and ES hits. */
object AtomTestHelpers {

    /** A sample IDF document of a download service with one coupled dataset. */
    const val SERVICE_IDF: String = """<?xml version="1.0" encoding="UTF-8"?>
<idf:html xmlns:idf="http://www.portalu.de/IDF/1.0"
          xmlns:gmd="http://www.isotc211.org/2005/gmd"
          xmlns:gco="http://www.isotc211.org/2005/gco"
          xmlns:gmx="http://www.isotc211.org/2005/gmx"
          xmlns:srv="http://www.isotc211.org/2005/srv">
  <idf:body>
    <idf:idfMdMetadata>
      <gmd:fileIdentifier><gco:CharacterString>service-uuid-1</gco:CharacterString></gmd:fileIdentifier>
      <gmd:identificationInfo>
        <gmd:SV_ServiceIdentification>
          <gmd:citation>
            <gmd:CI_Citation>
              <gmd:title><gco:CharacterString>Test Download Service</gco:CharacterString></gmd:title>
            </gmd:CI_Citation>
          </gmd:citation>
          <gmd:abstract><gco:CharacterString>Test service abstract</gco:CharacterString></gmd:abstract>
          <gmd:pointOfContact>
            <gmd:CI_ResponsibleParty>
              <gmd:organisationName><gco:CharacterString>Test Organisation</gco:CharacterString></gmd:organisationName>
              <gmd:contactInfo>
                <gmd:CI_Contact>
                  <gmd:address>
                    <gmd:CI_Address>
                      <gmd:electronicMailAddress><gco:CharacterString>test@example.org</gco:CharacterString></gmd:electronicMailAddress>
                    </gmd:CI_Address>
                  </gmd:address>
                </gmd:CI_Contact>
              </gmd:contactInfo>
            </gmd:CI_ResponsibleParty>
          </gmd:pointOfContact>
          <gmd:resourceConstraints>
            <gmd:MD_LegalConstraints>
              <gmd:accessConstraints>
                <gmd:MD_RestrictionCode codeListValue="otherRestrictions"/>
              </gmd:accessConstraints>
              <gmd:otherConstraints><gco:CharacterString>Test restriction</gco:CharacterString></gmd:otherConstraints>
            </gmd:MD_LegalConstraints>
          </gmd:resourceConstraints>
          <gmd:resourceConstraints>
            <gmd:MD_Constraints>
              <gmd:useLimitation><gco:CharacterString>Test use limitation</gco:CharacterString></gmd:useLimitation>
            </gmd:MD_Constraints>
          </gmd:resourceConstraints>
          <srv:operatesOn uuidref="dataset-uuid-1"/>
        </gmd:SV_ServiceIdentification>
      </gmd:identificationInfo>
      <gmd:dateStamp><gco:DateTime>2024-01-31T12:00:00</gco:DateTime></gmd:dateStamp>
    </idf:idfMdMetadata>
  </idf:body>
</idf:html>"""

    /** A sample IDF document of a coupled dataset with a download link. */
    const val DATASET_IDF: String = """<?xml version="1.0" encoding="UTF-8"?>
<idf:html xmlns:idf="http://www.portalu.de/IDF/1.0"
          xmlns:gmd="http://www.isotc211.org/2005/gmd"
          xmlns:gco="http://www.isotc211.org/2005/gco"
          xmlns:gmx="http://www.isotc211.org/2005/gmx">
  <idf:body>
    <idf:idfMdMetadata>
      <gmd:fileIdentifier><gco:CharacterString>dataset-uuid-1</gco:CharacterString></gmd:fileIdentifier>
      <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
          <gmd:citation>
            <gmd:CI_Citation>
              <gmd:title><gco:CharacterString>Test Dataset</gco:CharacterString></gmd:title>
              <gmd:identifier>
                <gmd:MD_Identifier>
                  <gmd:code><gco:CharacterString>https://example.org/datasets/dataset-1</gco:CharacterString></gmd:code>
                </gmd:MD_Identifier>
              </gmd:identifier>
            </gmd:CI_Citation>
          </gmd:citation>
          <gmd:abstract><gco:CharacterString>Test dataset abstract</gco:CharacterString></gmd:abstract>
          <gmd:pointOfContact>
            <gmd:CI_ResponsibleParty>
              <gmd:organisationName><gco:CharacterString>Dataset Organisation</gco:CharacterString></gmd:organisationName>
              <gmd:contactInfo>
                <gmd:CI_Contact>
                  <gmd:address>
                    <gmd:CI_Address>
                      <gmd:electronicMailAddress><gco:CharacterString>dataset@example.org</gco:CharacterString></gmd:electronicMailAddress>
                    </gmd:CI_Address>
                  </gmd:address>
                </gmd:CI_Contact>
              </gmd:contactInfo>
            </gmd:CI_ResponsibleParty>
          </gmd:pointOfContact>
          <gmd:resourceConstraints>
            <gmd:MD_Constraints>
              <gmd:useLimitation><gco:CharacterString>Dataset use limitation</gco:CharacterString></gmd:useLimitation>
            </gmd:MD_Constraints>
          </gmd:resourceConstraints>
          <gmd:extent>
            <gmd:EX_Extent>
              <gmd:geographicElement>
                <gmd:EX_GeographicBoundingBox>
                  <gmd:westBoundLongitude><gco:Decimal>5.5</gco:Decimal></gmd:westBoundLongitude>
                  <gmd:eastBoundLongitude><gco:Decimal>6.5</gco:Decimal></gmd:eastBoundLongitude>
                  <gmd:southBoundLatitude><gco:Decimal>49.5</gco:Decimal></gmd:southBoundLatitude>
                  <gmd:northBoundLatitude><gco:Decimal>50.5</gco:Decimal></gmd:northBoundLatitude>
                </gmd:EX_GeographicBoundingBox>
              </gmd:geographicElement>
            </gmd:EX_Extent>
          </gmd:extent>
        </gmd:MD_DataIdentification>
      </gmd:identificationInfo>
      <gmd:dateStamp><gco:Date>2024-02-01</gco:Date></gmd:dateStamp>
      <gmd:referenceSystemInfo>
        <gmd:MD_ReferenceSystem>
          <gmd:referenceSystemIdentifier>
            <gmd:RS_Identifier>
              <gmd:code><gco:CharacterString>EPSG:25832</gco:CharacterString></gmd:code>
              <gmd:codeSpace><gco:CharacterString>urn:ogc:def:crs:EPSG::25832</gco:CharacterString></gmd:codeSpace>
            </gmd:RS_Identifier>
          </gmd:referenceSystemIdentifier>
        </gmd:MD_ReferenceSystem>
      </gmd:referenceSystemInfo>
      <gmd:transferOptions>
        <gmd:MD_DigitalTransferOptions>
          <gmd:onLine>
            <gmd:CI_OnlineResource>
              <gmd:linkage><gmd:URL>https://example.org/data/dataset-1.zip</gmd:URL></gmd:linkage>
              <gmd:function><gmd:CI_OnLineFunctionCode codeListValue="download"/></gmd:function>
              <gmd:name><gco:CharacterString>Dataset download</gco:CharacterString></gmd:name>
            </gmd:CI_OnlineResource>
          </gmd:onLine>
        </gmd:MD_DigitalTransferOptions>
      </gmd:transferOptions>
    </idf:idfMdMetadata>
  </idf:body>
</idf:html>"""

    /** Builds an Elasticsearch hit containing the given IDF document. */
    fun esHit(
        id: String,
        idf: String,
    ): JsonObject =
        buildJsonObject {
            put("_index", "test-index")
            put("_id", id)
            put(
                "_source",
                buildJsonObject {
                    put("idf", idf)
                },
            )
        }
}
