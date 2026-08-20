package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

class WinnerCalculator(private val definitions: GameDefinitions) {

    fun calculateWealth(session: GameSession, playerId: String): Int {
        val cash = session.players[playerId]?.balance ?: 0
        val propertyValue = session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.properties[it.propertyId]!!.purchasePrice }
        return cash + propertyValue
    }

    fun highestPropertyValue(session: GameSession, playerId: String): Int =
        session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .maxOfOrNull { definitions.properties[it.propertyId]!!.purchasePrice }
            ?: 0

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
