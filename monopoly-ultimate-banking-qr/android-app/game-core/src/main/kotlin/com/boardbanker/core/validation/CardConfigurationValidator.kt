package com.boardbanker.core.validation

import com.boardbanker.core.model.CardConfiguration
import com.boardbanker.core.model.EditionDefinition

object CardConfigurationValidator {
    fun validate(edition: EditionDefinition): List<String> {
        val editionId = edition.editionId
        val config = edition.cardConfiguration
            ?: return listOf("Edition '$editionId': cardConfiguration is missing or invalid.")

        val problems = mutableListOf<String>()
        fun invalid(field: String, detail: String) {
            problems += "Edition '$editionId': cardConfiguration.$field is missing or invalid ($detail)."
        }

        if (config.playerCardCount <= 0) {
            invalid("playerCardCount", "must be greater than zero")
        }
        if (config.propertyCardCount <= 0) {
            invalid("propertyCardCount", "must be greater than zero")
        }
        if (config.eventCardCount < 0) {
            invalid("eventCardCount", "must not be negative")
        }
        if (config.rentLevelsPerProperty <= 0) {
            invalid("rentLevelsPerProperty", "must be greater than zero")
        }
        if (config.energyGridCardCount < 0) {
            invalid("energyGridCardCount", "must not be negative")
        }
        return problems
    }

    fun expectedTotalCards(config: CardConfiguration): Int =
        config.playerCardCount + config.propertyCardCount + config.eventCardCount + config.energyGridCardCount
}
