package com.boardbanker.app.gameplay.presentation

enum class DiceGambleStatus {
    WAITING_TO_ROLL,
    ROLLING,
    AWAITING_DEBT_RESOLUTION,
    COMPLETED,
}

data class LuckyBreakCompletedOutcome(
    val eventId: String,
    val actingPlayerId: String,
    val dieOne: Int,
    val dieTwo: Int,
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
)
