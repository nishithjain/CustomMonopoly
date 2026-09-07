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
        if (request.context == ScanContext.GET_OUT_OF_JAIL_PASS) {
            return validateGetOutOfJailPass(resolution, request)
        }
        if (request.context == ScanContext.RESOLVE_PENDING_EVENT_DRAW) {
            if (resolution.cardType != CardType.EVENT) {
                return CardTypeValidation.WrongCard(
                    "Wrong card type.\nPlease scan an Event Card from this edition.",
                )
            }
            return CardTypeValidation.Accepted
        }
        if (resolution.cardType !in request.acceptedCardTypes) {
            val expected = request.singleExpectedType ?: request.acceptedCardTypes.first()
            return CardTypeValidation.WrongType(expected = expected, actual = resolution.cardType)
        }
        if (request.specificCardId != null && resolution.cardId != request.specificCardId) {
            return CardTypeValidation.WrongCard(request.mismatchInstruction)
        }
        return CardTypeValidation.Accepted
    }

    private fun validateGetOutOfJailPass(
        resolution: CardResolution.Success,
        request: ScanRequest,
    ): CardTypeValidation {
        val allowedEventIds = request.allowedEventIds ?: emptySet()
        if (resolution.cardType != CardType.EVENT) {
            return CardTypeValidation.WrongCard(
                "Wrong card type.\nOnly the Get out of Jail Pass Event Card is accepted.",
            )
        }
        if (resolution.cardId !in allowedEventIds) {
            return CardTypeValidation.WrongCard(
                "This Event Card cannot be used to get out of Jail.\n" +
                    "Scan the Get out of Jail Pass Event Card.",
            )
        }
        return CardTypeValidation.Accepted
    }
}

sealed class CardTypeValidation {
    data object Accepted : CardTypeValidation()
    data class WrongType(val expected: CardType, val actual: CardType) : CardTypeValidation()
    data class WrongCard(val message: String) : CardTypeValidation()
}
