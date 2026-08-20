package com.boardbanker.core.card

import kotlinx.serialization.Serializable

@Serializable
data class CardDefinition(
    val cardId: String,
    val cardType: CardType,
    val name: String,
    val qrPayload: String,
)
