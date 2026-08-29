package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType

object ScannerCardFilter {
    fun validateCardType(
        resolution: CardResolution.Success,
        expectedCardType: CardType?,
    ): CardTypeValidation = validate(resolution, ScanRequest.fromExpectedType(expectedCardType))

    fun validate(
        resolution: CardResolution.Success,
        request: ScanRequest,
    ): CardTypeValidation {
        if (resolution.cardType !in request.acceptedCardTypes) {
            val expected = request.singleExpectedType ?: request.acceptedCardTypes.first()
            return CardTypeValidation.WrongType(expected = expected, actual = resolution.cardType)
        }
        if (request.specificCardId != null && resolution.cardId != request.specificCardId) {
            return CardTypeValidation.WrongCard(request.mismatchInstruction)
        }
        return CardTypeValidation.Accepted
    }
}

sealed class CardTypeValidation {
    data object Accepted : CardTypeValidation()
    data class WrongType(val expected: CardType, val actual: CardType) : CardTypeValidation()
    data class WrongCard(val message: String) : CardTypeValidation()
}
