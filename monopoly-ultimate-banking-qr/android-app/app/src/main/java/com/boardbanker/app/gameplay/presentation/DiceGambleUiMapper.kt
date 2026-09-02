package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object DiceGambleUiMapper {
    private const val INSTRUCTION =
        "Roll both dice up to three times. Roll doubles to collect the jackpot; otherwise pay the penalty."

    fun map(
        session: GameSession,
        definitions: GameDefinitions,
        commandInFlight: Boolean,
    ): DiceGambleUiState? {
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
            commandInFlight -> DiceGambleStatus.ROLLING
            session.debtResolution != null -> DiceGambleStatus.AWAITING_DEBT_RESOLUTION
            else -> DiceGambleStatus.WAITING_TO_ROLL
        }

        val rollEnabled = !commandInFlight &&
            session.debtResolution == null &&
            !pending.completed &&
            attemptsRemaining > 0

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
        )
    }

    fun successMessage(jackpotText: String, playerName: String): String =
        "Doubles! $playerName won $jackpotText"

    fun failureMessage(penaltyText: String): String =
        "No doubles. Pay $penaltyText"

    private fun pluralize(count: Int, word: String): String =
        if (count == 1) word else "${word}s"
}
