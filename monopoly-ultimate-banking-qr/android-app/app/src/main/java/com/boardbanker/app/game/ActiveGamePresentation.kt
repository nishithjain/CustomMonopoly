package com.boardbanker.app.game

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.ui.screens.game.PlayerDashboardUi
import com.boardbanker.app.ui.screens.playerdetails.OwnedPropertyUi
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.rules.RentLevelOperations

object ActiveGamePresentation {
    fun buildPlayerDashboard(session: GameSession, definitions: GameDefinitions): List<PlayerDashboardUi> =
        session.players.map { (playerId, playerState) ->
            val propertyCount = session.properties.values.count { it.ownerPlayerId == playerId }
            val jailLabel = if (playerState.jailStatus) "IN JAIL" else "Active"
            PlayerDashboardUi(
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, definitions),
                balanceText = formatMoney(playerState.balance),
                propertyCount = propertyCount,
                inJail = playerState.jailStatus,
                summaryLine = "$propertyCount Properties • $jailLabel",
            )
        }

    fun buildOwnedProperties(
        session: GameSession,
        playerId: String,
        definitions: GameDefinitions,
    ): List<OwnedPropertyUi> =
        session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sortedBy { it.propertyId }
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
                    propertyName = definition.name,
                    colorGroup = definition.colorGroup,
                    rentLevel = propertyState.currentRentLevel,
                    maxRentLevel = definition.maximumRentLevel,
                    currentRentText = formatMoney(currentRent),
                    purchasePriceText = formatMoney(definition.purchasePrice),
                )
            }
}
