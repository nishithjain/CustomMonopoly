package com.boardbanker.app.game

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.ui.screens.game.PlayerDashboardUi
import com.boardbanker.app.ui.screens.playerdetails.OwnedEnergyGridUi
import com.boardbanker.app.ui.screens.playerdetails.OwnedPropertyUi
import com.boardbanker.app.util.formatEnumLabel
import com.boardbanker.app.util.formatMoney
import com.boardbanker.app.util.pluralize
import com.boardbanker.core.model.EnergyGridDefinition
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.displayNameWithNumber
import com.boardbanker.core.rules.EnergyGridRentCalculator
import com.boardbanker.core.rules.PlayerActiveEventEffects
import com.boardbanker.core.rules.RentLevelOperations

object ActiveGamePresentation {
    fun buildPlayerDashboard(session: GameSession, definitions: GameDefinitions): List<PlayerDashboardUi> {
        val activePlayerId = session.turnState?.activePlayerId
        val hasEnergyGridsInEdition = definitions.energyGrids.isNotEmpty()
        return session.players.map { (playerId, playerState) ->
            val propertyCount = session.properties.values.count { it.ownerPlayerId == playerId }
            val energyGridCount = ownedEnergyGridCount(session, playerId)
            val activeEventNames = PlayerActiveEventEffects.activeEventNames(
                playerId = playerId,
                session = session,
                definitions = definitions,
            )
            val statusText = when {
                playerState.bankrupt -> "Bankrupt"
                playerState.jailStatus -> "In Jail"
                else -> "Active"
            }
            val assetsSummaryLine = buildAssetsSummaryLine(
                propertyCount = propertyCount,
                energyGridCount = energyGridCount,
                hasEnergyGridsInEdition = hasEnergyGridsInEdition,
            )
            PlayerDashboardUi(
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, definitions),
                balanceText = formatMoney(playerState.balance, definitions),
                propertyCount = propertyCount,
                energyGridCount = energyGridCount,
                hasEnergyGridsInEdition = hasEnergyGridsInEdition,
                inJail = playerState.jailStatus,
                isBankrupt = playerState.bankrupt,
                isActiveTurn = playerId == activePlayerId,
                statusText = statusText,
                assetsSummaryLine = assetsSummaryLine,
                summaryLine = assetsSummaryLine,
                activeEventLines = PlayerActiveEventDisplay.formatLines(activeEventNames),
            )
        }
    }

    fun buildAssetsSummaryLine(
        propertyCount: Int,
        energyGridCount: Int,
        hasEnergyGridsInEdition: Boolean,
    ): String {
        val properties = formatAssetCount(propertyCount, "Property", "Properties")
        if (!hasEnergyGridsInEdition) return properties
        val grids = formatAssetCount(energyGridCount, "Energy Grid", "Energy Grids")
        return "$properties • $grids"
    }

    private fun formatAssetCount(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    fun buildOwnedProperties(
        session: GameSession,
        playerId: String,
        definitions: GameDefinitions,
    ): List<OwnedPropertyUi> =
        session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sortedBy { PropertyDisplayNames.propertyNumber(it.propertyId) ?: Int.MAX_VALUE }
            .mapNotNull { propertyState ->
                val definition = definitions.properties[propertyState.propertyId] ?: return@mapNotNull null
                val chargeLevel = RentLevelOperations.effectiveChargeLevel(
                    propertyState,
                    session.temporaryEffects,
                )
                val currentRent = RentLevelOperations.rentAmount(
                    definition = definition,
                    propertyState = propertyState,
                    chargeLevelOverride = chargeLevel,
                )
                OwnedPropertyUi(
                    propertyId = propertyState.propertyId,
                    propertyName = definition.displayNameWithNumber(),
                    colorGroup = definition.colorGroup,
                    colorGroupLabel = formatEnumLabel(definition.colorGroup),
                    rentLevel = propertyState.currentRentLevel,
                    maxRentLevel = definition.maximumRentLevel,
                    currentRentText = formatMoney(currentRent, definitions),
                    purchasePriceText = formatMoney(definition.purchasePrice, definitions),
                )
            }

    fun buildOwnedEnergyGrids(
        session: GameSession,
        playerId: String,
        definitions: GameDefinitions,
    ): List<OwnedEnergyGridUi> {
        if (definitions.energyGrids.isEmpty()) return emptyList()
        val ownedCount = EnergyGridRentCalculator.ownedCount(session, playerId)
        val currentRent = EnergyGridRentCalculator.rentForOwner(definitions, session, playerId)
        val rentTierText = if (ownedCount > 0) {
            "Rent with $ownedCount owned ${pluralize(ownedCount, "grid")}"
        } else {
            null
        }
        return session.energyGrids.values
            .filter { it.ownerPlayerId == playerId }
            .sortedBy { gridState ->
                definitions.energyGrids[gridState.energyGridId]?.sequence ?: Int.MAX_VALUE
            }
            .mapNotNull { gridState ->
                val definition = definitions.energyGrids[gridState.energyGridId] ?: return@mapNotNull null
                toOwnedEnergyGridUi(
                    definition = definition,
                    definitions = definitions,
                    currentRent = currentRent,
                    rentTierText = rentTierText,
                )
            }
    }

    fun toOwnedEnergyGridUi(
        definition: EnergyGridDefinition,
        definitions: GameDefinitions,
        currentRent: Int,
        rentTierText: String?,
    ): OwnedEnergyGridUi {
        val boardPosition = definitions.boardLayout.boardNumberForEnergyGrid(definition.energyGridId)
        return OwnedEnergyGridUi(
            energyGridId = definition.energyGridId,
            energyGridName = definition.name,
            boardPosition = boardPosition,
            boardPositionLabel = boardPosition?.let { "Board $it" },
            categoryLabel = "Energy Grid",
            currentRentText = formatMoney(currentRent, definitions),
            purchasePriceText = formatMoney(definition.purchasePrice, definitions),
            rentTierText = rentTierText,
        )
    }

    fun ownedEnergyGridCount(session: GameSession, playerId: String): Int =
        session.energyGrids.values.count { it.ownerPlayerId == playerId }
}
