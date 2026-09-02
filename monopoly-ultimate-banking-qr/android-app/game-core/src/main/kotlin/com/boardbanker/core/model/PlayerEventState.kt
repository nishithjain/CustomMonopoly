package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingEventDraw(
    val parentEventId: String,
    val actingPlayerId: String,
    val chainDepth: Int,
    val maximumChainDepth: Int,
)

@Serializable
data class PendingDiceGamble(
    val eventId: String,
    val actingPlayerId: String,
    val attemptsUsed: Int,
    val maximumAttempts: Int,
    val jackpotAmount: Int,
    val penaltyAmount: Int,
    val diceCount: Int,
    val lastRollResults: List<Int> = emptyList(),
    val completed: Boolean = false,
)
