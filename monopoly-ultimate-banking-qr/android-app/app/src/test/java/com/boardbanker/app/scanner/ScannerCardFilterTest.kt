package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import org.junit.Assert.assertEquals
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

    @Test
    fun rejectsWrongSpecificCardWithoutAccepting() {
        val resolution = CardResolution.Success(
            cardId = "USR_02",
            cardType = CardType.USER,
            displayName = "Helicopter",
            qrPayload = "MUB:PL:HELICOPTER",
        )
        val request = ScanRequest.player(
            specificCardId = "USR_01",
            specificCardName = "Arun",
            useTokenForm = false,
        )
        val validation = ScannerCardFilter.validate(resolution, request)
        assertTrue(validation is CardTypeValidation.WrongCard)
        assertEquals("Please scan Arun's Player Card.", (validation as CardTypeValidation.WrongCard).message)
    }

    @Test
    fun luckyDrawEventScanRequest_rejectsPlayerCard() {
        val resolution = CardResolution.Success(
            cardId = "USR_01",
            cardType = CardType.USER,
            displayName = "Car",
            qrPayload = "MUB:PL:CAR",
        )
        val validation = ScannerCardFilter.validate(resolution, ScanRequest.event())
        assertTrue(validation is CardTypeValidation.WrongType)
        val wrongType = validation as CardTypeValidation.WrongType
        assertEquals(CardType.EVENT, wrongType.expected)
        assertEquals(CardType.USER, wrongType.actual)
    }
}
