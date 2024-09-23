package de.ingrid.ingridapi.portal.model
import kotlinx.serialization.Serializable

@Serializable
data class CatalogsResult(
    val totalHits: Long,
    val catalogs: List<Catalog>,
)

@Serializable
data class Catalog(
    val id: String,
    val name: String,
    val partner: List<String>,
    val isAddress: Boolean,
    val isMetadata: Boolean,
)
