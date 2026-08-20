package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCardFilterTest {
    @Test
    fun acceptsMatchingUserCard() {
        val resolution = CardResolution.Success(
            cardId = "USR_01",
            cardType = CardType.USER,
            displayName = "Car",
            qrPayload = "MUB:PL:CAR",
        )
        assertTrue(ScannerCardFilter.validateCardType(resolution, CardType.USER) is CardTypeValidation.Accepted)
    }

    @Test
    fun rejectsPropertyCardDuringUserSetup() {
        val resolution = CardResolution.Success(
            cardId = "PRP_01",
            cardType = CardType.PROPERTY,
            displayName = "Old Kent Road",
            qrPayload = "MUB:P:01",
        )
        val validation = ScannerCardFilter.validateCardType(resolution, CardType.USER)
        assertTrue(validation is CardTypeValidation.WrongType)
    }

    @Test
    fun rejectsEventCardDuringUserSetup() {
        val resolution = CardResolution.Success(
            cardId = "EVT_01",
            cardType = CardType.EVENT,
            displayName = "Event",
            qrPayload = "MUB:E:E01",
        )
        val validation = ScannerCardFilter.validateCardType(resolution, CardType.USER)
        assertTrue(validation is CardTypeValidation.WrongType)
    }

    @Test
    fun debugModeAcceptsAnyCard() {
        val resolution = CardResolution.Success(
            cardId = "PRP_01",
            cardType = CardType.PROPERTY,
            displayName = "Old Kent Road",
            qrPayload = "MUB:P:01",
        )
        assertTrue(ScannerCardFilter.validateCardType(resolution, null) is CardTypeValidation.Accepted)
    }
}
