package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerDefinition(
    val playerId: String,
    val qrPayload: String,
    val displayName: String,
    val displayColor: String = displayName,
)
