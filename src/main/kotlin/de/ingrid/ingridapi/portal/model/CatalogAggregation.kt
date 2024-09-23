package de.ingrid.ingridapi.portal.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Source(
    val dataSourceName: String,
    private val partner: JsonElement,
    private val datatype: JsonElement,
) {
    fun getPartner(): List<String> =
        if (partner is JsonPrimitive) {
            listOf(partner.content)
        } else {
            partner.jsonArray.map { it.jsonPrimitive.content }
        }

    fun getDatatype(): List<String> =
        if (datatype is JsonPrimitive) {
            listOf(datatype.content)
        } else {
            datatype.jsonArray.map { it.jsonPrimitive.content }
        }
}

@Serializable
data class Hit(
    val _source: Source,
)

@Serializable
data class HitsWrapper(
    val hits: List<Hit>,
)

@Serializable
data class Info(
    val hits: HitsWrapper,
)

@Serializable
data class JsonResponse(
    val key: String,
    val info: Info,
) {
    fun getSource(): Source? =
        info.hits.hits
            .firstOrNull()
            ?._source
}

@ExperimentalSerializationApi
@Serializable
data class HitSource(
    val title: String,
    @JsonNames("isfolder") val isFolder: Boolean? = false,
)
