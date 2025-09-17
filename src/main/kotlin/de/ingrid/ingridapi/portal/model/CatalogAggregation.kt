package de.ingrid.ingridapi.portal.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Source(
    @JsonNames("collection.name", "dataSourceName")
    val collectionName: String? = null,
    private val partner: JsonElement? = null,
    private val datatype: JsonElement? = null,
) {
    fun getPartner(): List<String> =
        if (partner is JsonPrimitive) {
            listOf(partner.content)
        } else {
            partner?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }

    fun getDatatype(): List<String> =
        if (datatype is JsonPrimitive) {
            listOf(datatype.content)
        } else {
            datatype?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Hit(
    @JsonNames("_source") val source: Source,
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
            ?.source
}

@ExperimentalSerializationApi
@Serializable
data class HitSource(
    private val title: String,
    @JsonNames("isfolder") val isFolder: Boolean? = false,
    @JsonNames("t01_object.obj_class") val docType: String? = null,
    @JsonNames("t02_address.typ") private val addressType: JsonElement? = null,
    private val datatype: JsonElement? = null,
    @JsonNames("t02_address.title") private val personTitle: JsonElement? = null,
    @JsonNames("t02_address.firstname") private val firstName: JsonElement? = null,
    @JsonNames("t02_address.lastname") private val lastName: JsonElement? = null,
    @JsonNames("organisation") private val organisation: JsonElement? = null,
) {
    fun getDatatype(): List<String> = getContentAsList(datatype)

    fun getAddressTypeDatatype(): String = getFirstContent(addressType)

    fun getTitle(): String {
        val personTitleString = getFirstContent(personTitle)
        val firstNameString = getFirstContent(firstName)
        val lastNameString = getFirstContent(lastName)

        if (lastNameString.isEmpty() && firstNameString.isEmpty()) return getFirstContent(organisation)

        val builder = StringBuilder()

        if (personTitleString.isNotEmpty())
            builder.append("$personTitleString")

        if (firstNameString.isNotEmpty())
            builder.append(" $firstNameString")

        if (lastNameString.isNotEmpty())
            builder.append(" $lastNameString")

        val person = builder.toString().trim()
        if (person.isNotEmpty())
            return person

        return title
    }

    private fun getFirstContent(element: JsonElement?): String =
        if (element is JsonPrimitive) {
            element.content
        } else {
            element?.jsonArray?.map { it.jsonPrimitive.content }?.firstOrNull() ?: ""
        }

    private fun getContentAsList(element: JsonElement?): List<String> =
        if (element is JsonPrimitive) {
            listOf(element.content)
        } else {
            element?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }
}
