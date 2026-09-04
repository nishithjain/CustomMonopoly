package com.boardbanker.core.validation

import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.BoardLayout
import com.boardbanker.core.model.BoardSpaceType
import com.boardbanker.core.model.CardConfiguration
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.rules.EnergyGridRentCalculator

class DefinitionValidator {

    fun validate(definitions: GameDefinitions): List<String> {
        val editionId = definitions.editionId
        val edition = definitions.edition
        val problems = mutableListOf<String>()

        if (edition == null) {
            problems += "Edition '$editionId': edition manifest is missing"
        } else {
            problems += CardConfigurationValidator.validate(edition)
        }

        val config = edition?.cardConfiguration
        if (config != null) {
            problems += validateCardCounts(definitions, editionId, config)
            problems += validateRentLevels(definitions, editionId, config)
        }

        if (definitions.players.isEmpty()) {
            problems += "Edition '$editionId': no player definitions loaded"
        }

        if (definitions.cardsByQrPayload.size != definitions.cards.size) {
            problems += "Edition '$editionId': duplicate QR payloads in card registry"
        }

        for (property in definitions.properties.values) {
            val colorGroup = definitions.boardRelationships.colorGroups[property.colorGroup]
            if (colorGroup == null) {
                problems += "Edition '$editionId', property '${property.propertyId}': unknown color group ${property.colorGroup}"
            } else if (!colorGroup.contains(property.propertyId)) {
                problems += "Edition '$editionId', property '${property.propertyId}': not in declared color group ${property.colorGroup}"
            }
        }

        for (event in definitions.events.values) {
            if (event.actions.isEmpty()) {
                problems += "Edition '$editionId', event '${event.eventId}': must define at least one action"
            }
        }

        for ((group, propertyIds) in definitions.boardRelationships.colorGroups) {
            for (propertyId in propertyIds) {
                if (!definitions.properties.containsKey(propertyId)) {
                    problems += "Edition '$editionId': color group $group references unknown property $propertyId"
                }
            }
        }

        for ((propertyId, neighbours) in definitions.boardRelationships.neighbours) {
            if (!definitions.properties.containsKey(propertyId)) {
                problems += "Edition '$editionId': neighbour mapping for unknown property $propertyId"
            }
            neighbours.forEach { neighbour ->
                if (!definitions.properties.containsKey(neighbour)) {
                    problems += "Edition '$editionId': neighbour $neighbour of $propertyId is unknown"
                }
            }
        }

        problems += validateBoardLayout(definitions)
        problems += validateBankingValues(definitions, editionId)

        return problems
    }

    private fun validateCardCounts(
        definitions: GameDefinitions,
        editionId: String,
        config: CardConfiguration,
    ): List<String> {
        val problems = mutableListOf<String>()
        val playerCards = definitions.cards.values.count { it.cardType == CardType.USER }
        val propertyCards = definitions.cards.values.count { it.cardType == CardType.PROPERTY }
        val eventCards = definitions.cards.values.count { it.cardType == CardType.EVENT }
        val energyGridCards = definitions.cards.values.count { it.cardType == CardType.ENERGY_GRID }
        val expectedTotal = CardConfigurationValidator.expectedTotalCards(config)

        if (definitions.players.size != config.playerCardCount) {
            problems += countMismatch(
                editionId,
                "Player Cards",
                config.playerCardCount,
                definitions.players.size,
            )
        }
        if (playerCards != config.playerCardCount) {
            problems += countMismatch(editionId, "Player Cards in registry", config.playerCardCount, playerCards)
        }
        if (definitions.properties.size != config.propertyCardCount) {
            problems += countMismatch(
                editionId,
                "Property Cards",
                config.propertyCardCount,
                definitions.properties.size,
            )
        }
        if (propertyCards != config.propertyCardCount) {
            problems += countMismatch(
                editionId,
                "Property Cards in registry",
                config.propertyCardCount,
                propertyCards,
            )
        }
        if (definitions.events.size != config.eventCardCount) {
            problems += countMismatch(
                editionId,
                "Event Cards",
                config.eventCardCount,
                definitions.events.size,
            )
        }
        if (eventCards != config.eventCardCount) {
            problems += countMismatch(
                editionId,
                "Event Cards in registry",
                config.eventCardCount,
                eventCards,
            )
        }
        if (definitions.energyGrids.size != config.energyGridCardCount) {
            problems += countMismatch(
                editionId,
                "Energy Grid Cards",
                config.energyGridCardCount,
                definitions.energyGrids.size,
            )
        }
        if (energyGridCards != config.energyGridCardCount) {
            problems += countMismatch(
                editionId,
                "Energy Grid Cards in registry",
                config.energyGridCardCount,
                energyGridCards,
            )
        }
        problems += EnergyGridRentCalculator.validateRentTable(definitions.energyGrids.values)
        if (definitions.cards.size != expectedTotal) {
            problems += "Edition '$editionId': expected $expectedTotal total cards from edition.json, but found ${definitions.cards.size}."
        }
        return problems
    }

    private fun validateRentLevels(
        definitions: GameDefinitions,
        editionId: String,
        config: CardConfiguration,
    ): List<String> {
        val problems = mutableListOf<String>()
        for (property in definitions.properties.values) {
            if (property.rentLevels.size != config.rentLevelsPerProperty) {
                problems += "Edition '$editionId', property '${property.propertyId}': expected ${config.rentLevelsPerProperty} rent levels, but found ${property.rentLevels.size}."
            }
        }
        return problems
    }

    private fun validateBoardLayout(definitions: GameDefinitions): List<String> {
        val editionId = definitions.editionId
        val layout = definitions.boardLayout
        val config = definitions.edition?.cardConfiguration
        val problems = mutableListOf<String>()

        if (layout.spaces.isEmpty()) {
            problems += "Edition '$editionId': board_layout.json contains no spaces"
            return problems
        }

        val positions = layout.spaces.map { it.position }
        if (positions.size != positions.toSet().size) {
            problems += "Edition '$editionId': board_layout.json contains duplicate positions"
        }
        val sortedPositions = positions.sorted()
        val expectedPositions = (0 until layout.spaces.size).toList()
        if (sortedPositions != expectedPositions) {
            problems += "Edition '$editionId': board_layout.json positions must be contiguous from 0 to ${layout.spaces.size - 1}"
        }

        val spaceIds = layout.spaces.map { it.spaceId.trim() }
        if (spaceIds.any { it.isEmpty() }) {
            problems += "Edition '$editionId': board_layout.json contains a blank spaceId"
        }
        if (spaceIds.size != spaceIds.toSet().size) {
            problems += "Edition '$editionId': board_layout.json contains duplicate spaceId values"
        }

        val propertySpaceTargets = mutableSetOf<String>()
        val energyGridSpaceTargets = mutableSetOf<String>()
        for (space in layout.spaces) {
            when (space.spaceType) {
                BoardSpaceType.PROPERTY -> {
                    val targetId = space.targetId?.trim().orEmpty()
                    if (targetId.isEmpty()) {
                        problems += "Edition '$editionId', space '${space.spaceId}': PROPERTY space requires targetId"
                    } else if (!definitions.properties.containsKey(targetId)) {
                        problems += "Edition '$editionId', space '${space.spaceId}': unknown property target '$targetId'"
                    } else {
                        propertySpaceTargets += targetId
                    }
                }
                BoardSpaceType.EVENT -> {
                    val deckId = space.deckId?.trim().orEmpty()
                    if (deckId.isEmpty()) {
                        problems += "Edition '$editionId', space '${space.spaceId}': EVENT space requires deckId"
                    } else if (deckId != MAIN_EVENT_DECK) {
                        problems += "Edition '$editionId', space '${space.spaceId}': unknown event deck '$deckId'"
                    } else if (definitions.events.isEmpty()) {
                        problems += "Edition '$editionId', space '${space.spaceId}': event deck '$deckId' has no cards"
                    }
                }
                BoardSpaceType.GO,
                BoardSpaceType.LOCATION,
                BoardSpaceType.ENERGY_GRID -> {
                    if (space.spaceType == BoardSpaceType.ENERGY_GRID) {
                        val targetId = space.targetId?.trim().orEmpty()
                        if (targetId.isEmpty()) {
                            problems += "Edition '$editionId', space '${space.spaceId}': ENERGY_GRID space requires targetId"
                        } else if (!definitions.energyGrids.containsKey(targetId)) {
                            problems += "Edition '$editionId', space '${space.spaceId}': unknown energy grid target '$targetId'"
                        } else {
                            energyGridSpaceTargets += targetId
                        }
                    }
                }
                BoardSpaceType.JAIL,
                BoardSpaceType.FREE_PARKING,
                BoardSpaceType.GO_TO_JAIL,
                -> Unit
            }
        }

        if (config != null) {
            if (propertySpaceTargets.size > config.propertyCardCount) {
                problems += "Edition '$editionId': expected at most ${config.propertyCardCount} PROPERTY board spaces, but found ${propertySpaceTargets.size}."
            }
            if (energyGridSpaceTargets.size != config.energyGridCardCount) {
                problems += "Edition '$editionId': expected ${config.energyGridCardCount} ENERGY_GRID board spaces, but found ${energyGridSpaceTargets.size}."
            }
            for (energyGridId in definitions.energyGrids.keys) {
                if (!energyGridSpaceTargets.contains(energyGridId)) {
                    problems += "Edition '$editionId', energy grid '$energyGridId': missing ENERGY_GRID board space"
                }
            }
        }

        for (propertyId in propertySpaceTargets) {
            if (!definitions.properties.containsKey(propertyId)) {
                problems += "Edition '$editionId', property board space references unknown property '$propertyId'"
            }
        }

        for ((propertyId, sideId) in definitions.boardRelationships.propertyToSide) {
            if (!definitions.properties.containsKey(propertyId)) {
                problems += "Edition '$editionId': board side mapping references unknown property '$propertyId'"
            } else if (!definitions.boardRelationships.boardSides.containsKey(sideId)) {
                problems += "Edition '$editionId', property '$propertyId': unknown board side '$sideId'"
            }
        }

        return problems
    }

    private fun countMismatch(
        editionId: String,
        label: String,
        expected: Int,
        actual: Int,
    ): String = "Edition '$editionId': expected $expected $label from edition.json, but found $actual."

    private fun validateBankingValues(definitions: GameDefinitions, editionId: String): List<String> {
        val problems = mutableListOf<String>()
        val banking = definitions.bankingValues
        if (banking.schemaVersion < 1) {
            problems += "Edition '$editionId': bankingValues.schemaVersion is missing or invalid"
        }
        if (banking.currency.code.isBlank() || banking.currency.symbol.isBlank()) {
            problems += "Edition '$editionId': bankingValues.currency is missing code or symbol"
        }
        if (banking.currency.scale < 1) {
            problems += "Edition '$editionId': bankingValues.currency.scale must be >= 1"
        }
        if (banking.startingBalance <= 0) {
            problems += "Edition '$editionId': bankingValues.startingBalance must be > 0"
        }
        if (banking.goSalary <= 0) {
            problems += "Edition '$editionId': bankingValues.goSalary must be > 0"
        }
        if (banking.locationFee <= 0) {
            problems += "Edition '$editionId': bankingValues.locationFee must be > 0"
        }
        if (banking.jailReleaseFee <= 0) {
            problems += "Edition '$editionId': bankingValues.jailReleaseFee must be > 0"
        }
        if (banking.auctionBidIncrement <= 0) {
            problems += "Edition '$editionId': bankingValues.auctionBidIncrement must be > 0"
        }
        if (banking.eventAmounts.m50 <= 0) {
            problems += "Edition '$editionId': bankingValues.eventAmounts.M50 must be > 0"
        }
        if (banking.eventAmounts.m200 <= 0) {
            problems += "Edition '$editionId': bankingValues.eventAmounts.M200 must be > 0"
        }
        return problems
    }

    fun isValid(definitions: GameDefinitions): Boolean = validate(definitions).isEmpty()

    companion object {
        const val MAIN_EVENT_DECK = "main"
    }
}
