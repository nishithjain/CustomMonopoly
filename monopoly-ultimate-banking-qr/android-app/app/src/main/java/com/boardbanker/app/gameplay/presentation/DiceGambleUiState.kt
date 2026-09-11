package com.boardbanker.app.gameplay.presentation

import com.boardbanker.core.model.DiceGambleMode

enum class DiceGambleStatus {
    SELECT_MODE,
    WAITING_TO_ROLL,
    ROLLING,
    PHYSICAL_DICE,
    PHYSICAL_CONFIRM_JACKPOT,
    PHYSICAL_CONFIRM_PENALTY,
    AWAITING_DEBT_RESOLUTION,
    COMPLETED,
}

data class LuckyBreakCompletedOutcome(
    val eventId: String,
    val actingPlayerId: String,
    val dieOne: Int?,
    val dieTwo: Int?,
    val headline: String,
    val outcomeMessage: String,
    val jackpotText: String,
    val penaltyText: String,
)

data class DiceGambleUiState(
    val eventId: String,
    val eventName: String,
    val playerId: String,
    val playerName: String,
    val attemptLabel: String,
    val maximumAttempts: Int,
    val dieOne: Int?,
    val dieTwo: Int?,
    val jackpotText: String,
    val penaltyText: String,
    val instruction: String,
    val status: DiceGambleStatus,
    val rollEnabled: Boolean,
    val rollButtonLabel: String = "Roll Dice",
    val showContinue: Boolean = false,
    val outcomeHeadline: String? = null,
    val outcomeMessage: String? = null,
    val mode: DiceGambleMode? = null,
    val physicalInstruction: String = PHYSICAL_INSTRUCTION,
    val physicalJackpotLabel: String = "",
    val physicalPenaltyLabel: String = "",
    val physicalConfirmMessage: String? = null,
) {
    companion object {
        const val PHYSICAL_INSTRUCTION =
            "Roll both physical dice up to three times.\n\n" +
                "If you roll doubles during any attempt, choose Jackpot.\n" +
                "If you do not roll doubles after all three attempts, choose Penalty."
    }
}
