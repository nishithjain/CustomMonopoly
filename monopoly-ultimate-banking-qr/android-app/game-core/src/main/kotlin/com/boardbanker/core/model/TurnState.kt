package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TurnState(
    val activePlayerId: String,
    val turnOrder: List<String>,
    val turnKind: TurnKind = TurnKind.NORMAL,
)
