package com.boardbanker.core.model

import kotlinx.serialization.Serializable

object EditionIds {
    /** Recognized legacy saves created before edition persistence was stored. */
    const val LEGACY_EDITION_ID = "uk"
    const val UK = "uk"
    const val INDIA = "india"

    /** Legacy saves created before edition definition versioning was persisted. */
    const val LEGACY_DEFINITION_VERSION = 1

    fun normalize(raw: String?): String {
        val value = raw?.trim().orEmpty()
        require(value.isNotEmpty()) { "editionId is required" }
        return value
    }
}

@Serializable
data class EditionDataFiles(
    val properties: String = "properties.json",
    val bankingValues: String = "banking_values.json",
    val events: String = "events.json",
    val boardRelationships: String = "board_relationships.json",
    val boardLayout: String = "board_layout.json",
    val cardRegistry: String = "card_registry.json",
    val eventEngineRules: String? = null,
    val gameRules: String? = "game_rules.json",
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
    val definitionVersion: Int,
    val name: String,
    val countryCode: String,
    val currency: CurrencyDefinition,
    val data: EditionDataFiles = EditionDataFiles(),
    val resources: EditionResourceRoots = EditionResourceRoots(),
    val artworkStatus: String = "READY",
    val cardConfiguration: CardConfiguration? = null,
)
