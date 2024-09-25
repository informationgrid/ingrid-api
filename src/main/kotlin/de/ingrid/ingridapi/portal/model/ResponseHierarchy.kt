package de.ingrid.ingridapi.portal.model
import kotlinx.serialization.Serializable

@Serializable
data class ResponseHierarchy(
    val uuid: String,
    val name: String,
    val docType: String,
    val hasChildren: Boolean,
    val isAddress: Boolean,
)
