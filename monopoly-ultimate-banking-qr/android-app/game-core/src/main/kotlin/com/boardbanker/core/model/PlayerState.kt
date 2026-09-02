package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val playerId: String,
    val playerName: String = "",
    val balance: Int,
    val active: Boolean = true,
    val bankrupt: Boolean = false,
    val jailStatus: Boolean = false,
    val pendingRentWaiver: Boolean = false,
    val jailPassCount: Int = 0,
    val pendingSkipTurnCount: Int = 0,
    val pendingExtraTurn: Boolean = false,
)
