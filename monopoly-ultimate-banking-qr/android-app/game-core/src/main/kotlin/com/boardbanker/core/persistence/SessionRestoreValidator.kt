package com.boardbanker.core.persistence

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

class SessionRestoreValidator(
    private val definitions: GameDefinitions,
) {
    fun validate(session: GameSession): List<String> {
        val problems = mutableListOf<String>()

        for (playerId in session.players.keys) {
            if (!definitions.players.containsKey(playerId) && !definitions.cards.containsKey(playerId)) {
                problems += "Unknown player reference: $playerId"
            }
        }

        for (propertyId in session.properties.keys) {
            if (!definitions.properties.containsKey(propertyId)) {
                problems += "Unknown property reference: $propertyId"
            }
        }

        for (propertyState in session.properties.values) {
            val definition = definitions.properties[propertyState.propertyId]
            if (definition != null) {
                val maxLevel = definition.maximumRentLevel
                if (propertyState.currentRentLevel < 1 || propertyState.currentRentLevel > maxLevel) {
                    problems += "${propertyState.propertyId}: invalid rent level ${propertyState.currentRentLevel}"
                }
            }
            propertyState.ownerPlayerId?.let { ownerId ->
                if (!session.players.containsKey(ownerId)) {
                    problems += "${propertyState.propertyId}: unknown owner $ownerId"
                }
            }
        }

        for (effect in session.temporaryEffects) {
            if (!definitions.events.containsKey(effect.createdByEventId)) {
                problems += "Unknown event reference in temporary effect: ${effect.createdByEventId}"
            }
        }

        session.debtResolution?.let { debt ->
            if (!session.players.containsKey(debt.debtorPlayerId)) {
                problems += "Debt references unknown debtor ${debt.debtorPlayerId}"
            }
            if (!session.players.containsKey(debt.creditorPlayerId)) {
                problems += "Debt references unknown creditor ${debt.creditorPlayerId}"
            }
        }

        session.auction?.let { auction ->
            if (!definitions.properties.containsKey(auction.propertyId)) {
                problems += "Auction references unknown property ${auction.propertyId}"
            }
        }

        session.pendingEventChoice?.let { choice ->
            if (!definitions.events.containsKey(choice.eventId)) {
                problems += "Pending event choice references unknown event ${choice.eventId}"
            }
        }

        session.winnerPlayerId?.let { winnerId ->
            if (!session.players.containsKey(winnerId)) {
                problems += "Winner references unknown player $winnerId"
            }
        }

        return problems
    }

    fun isValid(session: GameSession): Boolean = validate(session).isEmpty()
}
