package com.boardbanker.app.gameplay.presentation

data class EventDrawUiState(
    val parentEventId: String,
    val parentEventName: String,
    val actingPlayerId: String,
    val actingPlayerName: String,
    val instruction: String,
    val chainProgressText: String?,
    val scanEnabled: Boolean,
)
