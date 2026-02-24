package de.ingrid.ingridapi.core.services

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonElement?.asSafeString(): String =
    when (this) {
        null -> ""
        is JsonPrimitive -> contentOrNull ?: ""
        else -> toString()
    }
