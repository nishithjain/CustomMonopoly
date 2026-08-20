package com.boardbanker.core.card

sealed class CardResolution {
    data class Success(
        val cardId: String,
        val cardType: CardType,
        val displayName: String,
        val qrPayload: String,
    ) : CardResolution()

    data class UnknownQr(
        val qrPayload: String,
    ) : CardResolution()
}
