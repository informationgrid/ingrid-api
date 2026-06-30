package de.ingrid.ingridapi.ogc.records

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val errorJson = Json { prettyPrint = true; encodeDefaults = true }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InvalidParameterError(
    @EncodeDefault
    val code: String = "InvalidParameterValue",
    val description: String,
    val parameter: String,
    val invalidValue: String,
    val allowedValues: List<String>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotAcceptableError(
    @EncodeDefault
    val code: String = "NotAcceptable",
    val description: String,
    val links: List<Link>,
)

/**
 * Returns true if the Accept header indicates a browser-style request that should
 * fall back to HTML when no explicit format parameter matches.
 */
fun acceptsHtml(acceptHeader: String?): Boolean {
    if (acceptHeader.isNullOrBlank()) return false
    val lower = acceptHeader.lowercase()
    return lower.contains("text/html") || lower.contains("*/*")
}

suspend fun respondInvalidFormatParameter(
    call: ApplicationCall,
    invalidValue: String,
    allowedValues: List<String>,
) {
    val payload =
        InvalidParameterError(
            description = "The value '$invalidValue' is not a supported format for the parameter 'f'.",
            parameter = "f",
            invalidValue = invalidValue,
            allowedValues = allowedValues,
        )
    call.respondText(
        text = errorJson.encodeToString(payload),
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.BadRequest,
    )
}

suspend fun respondNotAcceptable(
    call: ApplicationCall,
    requestedType: String,
    alternateLinks: List<Link>,
) {
    val payload =
        NotAcceptableError(
            description = "The requested media type '$requestedType' is not supported for this resource.",
            links = alternateLinks,
        )
    call.respondText(
        text = errorJson.encodeToString(payload),
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.NotAcceptable,
    )
}
