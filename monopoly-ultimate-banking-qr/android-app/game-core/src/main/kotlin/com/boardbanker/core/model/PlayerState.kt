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
)
