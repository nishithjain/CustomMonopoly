package com.boardbanker.core.persistence

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus

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

        session.pendingEventExecution?.let { pending ->
            if (!definitions.events.containsKey(pending.eventId)) {
                problems += "Pending event execution references unknown event ${pending.eventId}"
            }
            if (!session.players.containsKey(pending.actingPlayerId)) {
                problems += "Pending event execution references unknown acting player ${pending.actingPlayerId}"
            }
            val event = definitions.events[pending.eventId]
            if (event != null && pending.currentActionIndex !in event.actions.indices) {
                problems += "Pending event execution has invalid action index ${pending.currentActionIndex}"
            }
        }

        session.winnerPlayerId?.let { winnerId ->
            if (!session.players.containsKey(winnerId)) {
                problems += "Winner references unknown player $winnerId"
            }
        }

        session.pendingDiceGamble?.let { gamble ->
            if (!definitions.events.containsKey(gamble.eventId)) {
                problems += "Pending dice gamble references unknown event ${gamble.eventId}"
            }
            if (!session.players.containsKey(gamble.actingPlayerId)) {
                problems += "Pending dice gamble references unknown player ${gamble.actingPlayerId}"
            }
        }

        session.pendingEventDraw?.let { draw ->
            if (!definitions.events.containsKey(draw.parentEventId)) {
                problems += "Pending event draw references unknown parent event ${draw.parentEventId}"
            }
            if (!session.players.containsKey(draw.actingPlayerId)) {
                problems += "Pending event draw references unknown player ${draw.actingPlayerId}"
            }
            if (draw.chainDepth < 1 || draw.chainDepth > draw.maximumChainDepth) {
                problems += "Pending event draw has invalid chain depth ${draw.chainDepth}"
            }
        }

        if (session.status == GameStatus.ACTIVE && session.turnState == null) {
            problems += "Active game is missing turn state"
        }

        return problems
    }

    fun isValid(session: GameSession): Boolean = validate(session).isEmpty()
}
