package com.boardbanker.core.model

import kotlinx.serialization.Serializable

object EditionIds {
    const val DEFAULT = "uk"
    const val UK = "uk"
    const val INDIA = "india"

    fun normalize(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return value.ifEmpty { DEFAULT }
    }
}

@Serializable
data class EditionDataFiles(
    val properties: String = "properties.json",
    val bankingValues: String = "banking_values.json",
    val events: String = "events.json",
    val boardRelationships: String = "board_relationships.json",
)

@Serializable
data class EditionResourceRoots(
    val propertyCards: String = "",
    val eventCards: String = "",
)

@Serializable
data class EditionDefinition(
    val schemaVersion: Int = 1,
    val editionId: String,
    val name: String,
    val countryCode: String,
    val currency: CurrencyDefinition,
    val data: EditionDataFiles = EditionDataFiles(),
    val resources: EditionResourceRoots = EditionResourceRoots(),
    val artworkStatus: String = "READY",
)
