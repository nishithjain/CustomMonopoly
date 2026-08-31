package com.boardbanker.app.cards

import com.boardbanker.core.card.CardType

data class CardFrontLookupKey(
    val editionId: String,
    val cardType: CardType,
    val cardId: String,
) {
    fun cacheKey(): String = "${editionId}:${cardType.name}:$cardId"
}

sealed interface CardFrontResolveResult {
    data class Found(val image: CardFrontImage) : CardFrontResolveResult

    data class Missing(
        val editionId: String,
        val cardType: CardType,
        val cardId: String,
        val reason: String,
    ) : CardFrontResolveResult
}
