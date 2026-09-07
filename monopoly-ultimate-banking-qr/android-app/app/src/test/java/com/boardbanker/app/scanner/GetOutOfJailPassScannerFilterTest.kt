package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetOutOfJailPassScannerFilterTest {
    private val request = ScanRequest.getOutOfJailPass(setOf("EVT_11"))

    @Test
    fun acceptsMatchingJailPassEvent() {
        val resolution = CardResolution.Success(
            cardId = "EVT_11",
            cardType = CardType.EVENT,
            displayName = "Get Out of Jail Pass",
            qrPayload = "MUB:E:E11",
        )
        assertTrue(ScannerCardFilter.validate(resolution, request) is CardTypeValidation.Accepted)
    }

    @Test
    fun rejectsDifferentEventCard() {
        val resolution = CardResolution.Success(
            cardId = "EVT_01",
            cardType = CardType.EVENT,
            displayName = "Lucky Draw",
            qrPayload = "MUB:E:E01",
        )
        val validation = ScannerCardFilter.validate(resolution, request)
        assertTrue(validation is CardTypeValidation.WrongCard)
        assertEquals(
            "This Event Card cannot be used to get out of Jail.\n" +
                "Scan the Get out of Jail Pass Event Card.",
            (validation as CardTypeValidation.WrongCard).message,
        )
    }

    @Test
    fun rejectsPropertyCard() {
        val resolution = CardResolution.Success(
            cardId = "PRP_01",
            cardType = CardType.PROPERTY,
            displayName = "Old Kent Road",
            qrPayload = "MUB:P:01",
        )
        val validation = ScannerCardFilter.validate(resolution, request)
        assertTrue(validation is CardTypeValidation.WrongCard)
        assertEquals(
            "Wrong card type.\nOnly the Get out of Jail Pass Event Card is accepted.",
            (validation as CardTypeValidation.WrongCard).message,
        )
    }

    @Test
    fun rejectsPlayerCard() {
        val resolution = CardResolution.Success(
            cardId = "USR_01",
            cardType = CardType.USER,
            displayName = "Car",
            qrPayload = "MUB:PL:CAR",
        )
        val validation = ScannerCardFilter.validate(resolution, request)
        assertTrue(validation is CardTypeValidation.WrongCard)
    }

    @Test
    fun rejectsEnergyGridCard() {
        val resolution = CardResolution.Success(
            cardId = "EG_01",
            cardType = CardType.ENERGY_GRID,
            displayName = "Energy Grid",
            qrPayload = "MUB:EG:01",
        )
        val validation = ScannerCardFilter.validate(resolution, request)
        assertTrue(validation is CardTypeValidation.WrongCard)
    }

    @Test
    fun scanRequest_usesRestrictedContextAndInstructions() {
        assertEquals(ScanContext.GET_OUT_OF_JAIL_PASS, request.context)
        assertEquals("Scan Get out of Jail Pass", request.instruction)
        assertEquals(
            "Only the Get out of Jail Pass Event Card is accepted.",
            request.supportingInstruction,
        )
    }
}
