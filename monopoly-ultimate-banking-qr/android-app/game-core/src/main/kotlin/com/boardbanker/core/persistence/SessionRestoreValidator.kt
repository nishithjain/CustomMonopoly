package com.boardbanker.core.persistence

import com.boardbanker.core.model.EntityRef
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

        for (energyGridId in session.energyGrids.keys) {
            if (!definitions.energyGrids.containsKey(energyGridId)) {
                problems += "Unknown energy grid reference: $energyGridId"
            }
        }

        for (gridState in session.energyGrids.values) {
            gridState.ownerPlayerId?.let { ownerId ->
                if (!session.players.containsKey(ownerId)) {
                    problems += "${gridState.energyGridId}: unknown owner $ownerId"
                }
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
            if (debt.creditorPlayerId != EntityRef.BANK &&
                !session.players.containsKey(debt.creditorPlayerId)
            ) {
                problems += "Debt references unknown creditor ${debt.creditorPlayerId}"
            }
            debt.eventContributorDebt?.let { contributorDebt ->
                if (!definitions.events.containsKey(contributorDebt.eventId)) {
                    problems += "Contributor debt references unknown event ${contributorDebt.eventId}"
                }
                if (!session.players.containsKey(contributorDebt.recipientPlayerId)) {
                    problems += "Contributor debt references unknown recipient ${contributorDebt.recipientPlayerId}"
                }
                if (!session.players.containsKey(contributorDebt.contributorPlayerId)) {
                    problems += "Contributor debt references unknown contributor ${contributorDebt.contributorPlayerId}"
                }
                if (contributorDebt.contributorPlayerId != debt.debtorPlayerId) {
                    problems += "Contributor debt debtor mismatch"
                }
            }
            debt.eventDebt?.let { eventDebt ->
                if (!definitions.events.containsKey(eventDebt.eventId)) {
                    problems += "Event debt references unknown event ${eventDebt.eventId}"
                }
                if (!session.players.containsKey(eventDebt.payerPlayerId)) {
                    problems += "Event debt references unknown payer ${eventDebt.payerPlayerId}"
                }
            }
            debt.eventBankDebit?.let { eventBankDebit ->
                if (!definitions.events.containsKey(eventBankDebit.eventId)) {
                    problems += "Event bank debit references unknown event ${eventBankDebit.eventId}"
                }
                if (!session.players.containsKey(eventBankDebit.debtorPlayerId)) {
                    problems += "Event bank debit references unknown debtor ${eventBankDebit.debtorPlayerId}"
                }
                if (eventBankDebit.debtorPlayerId != debt.debtorPlayerId) {
                    problems += "Event bank debit debtor mismatch"
                }
            }
        }

        session.pendingEventMultiContributorSettlement?.let { settlement ->
            if (!definitions.events.containsKey(settlement.eventId)) {
                problems += "Pending contributor settlement references unknown event ${settlement.eventId}"
            }
            if (!session.players.containsKey(settlement.recipientPlayerId)) {
                problems += "Pending contributor settlement references unknown recipient ${settlement.recipientPlayerId}"
            }
            settlement.contributorPlayerIds.forEach { contributorId ->
                if (!session.players.containsKey(contributorId)) {
                    problems += "Pending contributor settlement references unknown contributor $contributorId"
                }
            }
            settlement.completedTransfers.forEach { transfer ->
                if (!session.players.containsKey(transfer.fromPlayerId)) {
                    problems += "Pending contributor settlement references unknown payer ${transfer.fromPlayerId}"
                }
                if (!session.players.containsKey(transfer.toPlayerId)) {
                    problems += "Pending contributor settlement references unknown recipient ${transfer.toPlayerId}"
                }
                if (transfer.toPlayerId != settlement.recipientPlayerId) {
                    problems += "Pending contributor settlement transfer recipient mismatch"
                }
            }
            settlement.skippedContributorIds.forEach { contributorId ->
                if (!session.players.containsKey(contributorId)) {
                    problems += "Pending contributor settlement references unknown skipped contributor $contributorId"
                }
            }
        }

        session.auction?.let { auction ->
            auction.propertyId?.let { propertyId ->
                if (!definitions.properties.containsKey(propertyId)) {
                    problems += "Auction references unknown property $propertyId"
                }
            }
            auction.energyGridId?.let { energyGridId ->
                if (!definitions.energyGrids.containsKey(energyGridId)) {
                    problems += "Auction references unknown energy grid $energyGridId"
                }
            }
        }

        session.pendingEnergyGridLanding?.let { landing ->
            if (!definitions.energyGrids.containsKey(landing.energyGridId)) {
                problems += "Pending energy grid landing references unknown grid ${landing.energyGridId}"
            }
            if (!session.players.containsKey(landing.actingPlayerId)) {
                problems += "Pending energy grid landing references unknown visitor ${landing.actingPlayerId}"
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

        session.pendingEventResolution?.let { resolution ->
            if (!definitions.events.containsKey(resolution.eventId)) {
                problems += "Pending event resolution references unknown event ${resolution.eventId}"
            }
            if (!session.players.containsKey(resolution.actingPlayerId)) {
                problems += "Pending event resolution references unknown acting player ${resolution.actingPlayerId}"
            }
            resolution.obligations.forEach { obligation ->
                if (!session.players.containsKey(obligation.payerId)) {
                    problems += "Pending event resolution references unknown payer ${obligation.payerId}"
                }
                if (obligation.recipientId != EntityRef.BANK &&
                    !session.players.containsKey(obligation.recipientId)
                ) {
                    problems += "Pending event resolution references unknown recipient ${obligation.recipientId}"
                }
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
            if (draw.remainingDraws < 1) {
                problems += "Pending event draw has no remaining draws"
            }
        }

        if (session.status == GameStatus.ACTIVE && session.turnState == null) {
            problems += "Active game is missing turn state"
        }

        return problems
    }

    fun isValid(session: GameSession): Boolean = validate(session).isEmpty()
}
