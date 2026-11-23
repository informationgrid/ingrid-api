package de.ingrid.ingridapi

import java.util.Properties

object AppVersion {
    val value: String =
        AppVersion::class.java.getResourceAsStream("/ingrid-api-version.properties")
            ?.use { input -> Properties().apply { load(input) }.getProperty("version") }
            ?.takeIf { it.isNotBlank() }
            ?: "development"
}