package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType

object DiceGambleUiMapper {
    private const val INSTRUCTION =
        "Roll both dice up to three times. Roll doubles to collect the jackpot; otherwise pay the penalty."

    fun map(
        session: GameSession,
        definitions: GameDefinitions,
        rollInProgress: Boolean,
        completedOutcome: LuckyBreakCompletedOutcome? = null,
    ): DiceGambleUiState? {
        if (completedOutcome != null) {
            val event = definitions.events[completedOutcome.eventId] ?: return null
            val playerName = PlayerDisplayNames.displayName(session, completedOutcome.actingPlayerId, definitions)
            return DiceGambleUiState(
                eventId = completedOutcome.eventId,
                eventName = event.name,
                playerId = completedOutcome.actingPlayerId,
                playerName = playerName,
                attemptLabel = "",
                maximumAttempts = 3,
                dieOne = completedOutcome.dieOne,
                dieTwo = completedOutcome.dieTwo,
                jackpotText = completedOutcome.jackpotText,
                penaltyText = completedOutcome.penaltyText,
                instruction = INSTRUCTION,
                status = DiceGambleStatus.COMPLETED,
                rollEnabled = false,
                rollButtonLabel = "Continue",
                showContinue = true,
                outcomeHeadline = completedOutcome.headline,
                outcomeMessage = completedOutcome.outcomeMessage,
            )
        }

        val pending = session.pendingDiceGamble ?: return null
        val event = definitions.events[pending.eventId] ?: return null
        val playerName = PlayerDisplayNames.displayName(session, pending.actingPlayerId, definitions)
        val attemptsRemaining = pending.maximumAttempts - pending.attemptsUsed
        val dieOne = pending.lastRollResults.getOrNull(0)
        val dieTwo = pending.lastRollResults.getOrNull(1)

        val attemptLabel = when {
            pending.attemptsUsed == 0 && dieOne == null ->
                "Attempt 1 of ${pending.maximumAttempts}"
            attemptsRemaining > 0 ->
                "No doubles — $attemptsRemaining ${pluralize(attemptsRemaining, "attempt")} remaining"
            else -> "Attempt ${pending.maximumAttempts} of ${pending.maximumAttempts}"
        }

        val status = when {
            rollInProgress -> DiceGambleStatus.ROLLING
            session.debtResolution != null -> DiceGambleStatus.AWAITING_DEBT_RESOLUTION
            else -> DiceGambleStatus.WAITING_TO_ROLL
        }

        val rollEnabled = !rollInProgress &&
            session.debtResolution == null &&
            !pending.completed &&
            attemptsRemaining > 0

        val rollButtonLabel = when {
            rollInProgress -> "Rolling..."
            pending.attemptsUsed > 0 && attemptsRemaining > 0 -> "Roll Again"
            else -> "Roll Dice"
        }

        return DiceGambleUiState(
            eventId = pending.eventId,
            eventName = event.name,
            playerId = pending.actingPlayerId,
            playerName = playerName,
            attemptLabel = attemptLabel,
            maximumAttempts = pending.maximumAttempts,
            dieOne = dieOne,
            dieTwo = dieTwo,
            jackpotText = formatMoney(pending.jackpotAmount, definitions),
            penaltyText = formatMoney(pending.penaltyAmount, definitions),
            instruction = INSTRUCTION,
            status = status,
            rollEnabled = rollEnabled,
            rollButtonLabel = rollButtonLabel,
        )
    }

    fun buildCompletedOutcome(
        session: GameSession,
        definitions: GameDefinitions,
        eventId: String,
        actingPlayerId: String,
        dieOne: Int,
        dieTwo: Int,
        transactions: List<com.boardbanker.core.model.Transaction>,
        jackpotAmount: Int,
        penaltyAmount: Int,
    ): LuckyBreakCompletedOutcome {
        val playerName = PlayerDisplayNames.displayName(session, actingPlayerId, definitions)
        val headline = if (dieOne == dieTwo) "Doubles!" else "No doubles"
        val creditTx = transactions.lastOrNull { it.transactionType == TransactionType.BANK_CREDIT }
        val debitTx = transactions.lastOrNull { it.transactionType == TransactionType.BANK_DEBIT }
        val outcomeMessage = when {
            creditTx != null -> "$playerName collected ${formatMoney(creditTx.amount ?: 0, definitions)}."
            debitTx != null -> "$playerName paid ${formatMoney(debitTx.amount ?: 0, definitions)}."
            else -> "Lucky Break resolved."
        }
        return LuckyBreakCompletedOutcome(
            eventId = eventId,
            actingPlayerId = actingPlayerId,
            dieOne = dieOne,
            dieTwo = dieTwo,
            headline = headline,
            outcomeMessage = outcomeMessage,
            jackpotText = formatMoney(jackpotAmount, definitions),
            penaltyText = formatMoney(penaltyAmount, definitions),
        )
    }

    fun successMessage(jackpotText: String, playerName: String): String =
        "Doubles! $playerName won $jackpotText"

    fun failureMessage(penaltyText: String): String =
        "No doubles. Pay $penaltyText"

    private fun pluralize(count: Int, word: String): String =
        if (count == 1) word else "${word}s"
}
