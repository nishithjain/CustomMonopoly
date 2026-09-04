package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

class WinnerCalculator(private val definitions: GameDefinitions) {

    fun calculateWealth(session: GameSession, playerId: String): Int {
        val cash = session.players[playerId]?.balance ?: 0
        return cash + totalAssetValue(session, playerId)
    }

    fun highestPropertyValue(session: GameSession, playerId: String): Int {
        val propertyValues = session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .map { definitions.properties[it.propertyId]!!.purchasePrice }
        val energyGridValues = session.energyGrids.values
            .filter { it.ownerPlayerId == playerId }
            .map { definitions.energyGrids[it.energyGridId]!!.purchasePrice }
        return (propertyValues + energyGridValues).maxOrNull() ?: 0
    }

    private fun totalAssetValue(session: GameSession, playerId: String): Int {
        val propertyValue = session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.properties[it.propertyId]!!.purchasePrice }
        val energyGridValue = session.energyGrids.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.energyGrids[it.energyGridId]!!.purchasePrice }
        return propertyValue + energyGridValue
    }

    fun determineWinner(session: GameSession): String? {
        val activePlayers = session.players.values.filter { !it.bankrupt }
        val candidates = if (activePlayers.isNotEmpty()) {
            activePlayers.map { it.playerId }
        } else {
            session.players.keys.toList()
        }

        return candidates.maxWithOrNull(
            compareBy<String> { calculateWealth(session, it) }
                .thenBy { highestPropertyValue(session, it) },
        )
    }
}
