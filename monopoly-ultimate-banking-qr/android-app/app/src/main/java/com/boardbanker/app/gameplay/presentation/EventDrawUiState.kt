package com.boardbanker.app.gameplay.presentation

data class EventDrawUiState(
    val parentEventId: String,
    val parentEventName: String,
    val actingPlayerId: String,
    val actingPlayerName: String,
    val instruction: String,
    val requiredDrawsText: String,
    val scanButtonLabel: String,
    val scanEnabled: Boolean,
)
