package com.boardbanker.app.gameplay.presentation

enum class DiceGambleStatus {
    WAITING_TO_ROLL,
    ROLLING,
    AWAITING_DEBT_RESOLUTION,
}

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
)
