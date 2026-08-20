package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType

object ScannerCardFilter {
    fun validateCardType(
        resolution: CardResolution.Success,
        expectedCardType: CardType?,
    ): CardTypeValidation {
        if (expectedCardType == null) {
            return CardTypeValidation.Accepted
        }
        return if (resolution.cardType == expectedCardType) {
            CardTypeValidation.Accepted
        } else {
            CardTypeValidation.WrongType(expected = expectedCardType, actual = resolution.cardType)
        }
    }
}

sealed class CardTypeValidation {
    data object Accepted : CardTypeValidation()
    data class WrongType(val expected: CardType, val actual: CardType) : CardTypeValidation()
}
