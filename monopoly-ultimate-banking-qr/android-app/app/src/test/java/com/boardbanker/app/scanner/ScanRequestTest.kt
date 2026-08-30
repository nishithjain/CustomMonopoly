package com.boardbanker.app.scanner

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.scanner.delivery.ScanResultConsumer
import com.boardbanker.app.scanner.delivery.ScanResultDeliverer
import com.boardbanker.app.ui.screens.game.GameUiState
import com.boardbanker.app.ui.screens.game.withScanRequest
import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRequestTest {
    private val definitions = AppTestSupport.definitions

    @Test
    fun headingAndOverlayAlwaysMatch() {
        val requests = listOf(
            ScanRequest.gameCard(),
            ScanRequest.player(),
            ScanRequest.property(),
            ScanRequest.event(),
            ScanRequest.playerOrProperty(),
            ScanRequest.undoAuthorization(2),
            ScanRequest.forPropertyId("PRP_01", definitions),
            ScanRequest.forEventId("EVT_01", definitions),
            ScanRequest.forPlayerId("USR_04", AppTestSupport.newGame(listOf("USR_04")), definitions),
        )
        requests.forEach { request ->
            assertEquals(request.instruction, request.heading)
            assertEquals(request.instruction, request.overlayInstruction)
        }
    }

    @Test
    fun playerTypeInstruction() {
        assertEquals("Scan a Player Card", ScanRequest.player().instruction)
        assertEquals("Please scan a Player Card.", ScanRequest.player().mismatchInstruction)
    }

    @Test
    fun propertyTypeInstruction() {
        assertEquals("Scan a Property Card", ScanRequest.property().instruction)
    }

    @Test
    fun eventTypeInstruction() {
        assertEquals("Scan an Event Card", ScanRequest.event().instruction)
    }

    @Test
    fun namedPlayerUsesRegisteredName() {
        val session = AppTestSupport.newGame(listOf("USR_01", "USR_04"))
        val request = ScanRequest.forPlayerId("USR_04", session, definitions)
        assertEquals("Scan Arun's Player Card", request.instruction)
        assertFalse(request.instruction.contains("USR_04"))
        assertFalse(request.instruction.contains("QR"))
    }

    @Test
    fun unnamedPlayerUsesTokenName() {
        val request = ScanRequest.player(
            specificCardId = "USR_01",
            specificCardName = "Car",
            useTokenForm = true,
        )
        assertEquals("Scan the Car Player Card", request.instruction)
        assertEquals("Please scan the Car Player Card.", request.mismatchInstruction)
    }

    @Test
    fun namedPropertyUsesFriendlyName() {
        val request = ScanRequest.forPropertyId("PRP_01", definitions)
        assertEquals("Scan [1] Old Kent Road Property Card", request.instruction)
        assertFalse(request.instruction.contains("PRP_01"))
        assertFalse(request.instruction.contains("E01_"))
        assertFalse(request.instruction.contains("Back_QR"))
    }

    @Test
    fun namedEventUsesFriendlyName() {
        val request = ScanRequest.forEventId("EVT_01", definitions)
        assertEquals("Scan Boom Town Event Card", request.instruction)
        assertFalse(request.instruction.contains("EVT_01"))
        assertFalse(request.instruction.contains("E01_Boom_Town_Back_QR"))
    }

    @Test
    fun genericGameCardOnlyWhenAnyTypeAccepted() {
        val any = ScanRequest.gameCard()
        assertEquals("Scan a Game Card", any.instruction)
        assertEquals(setOf(CardType.USER, CardType.PROPERTY, CardType.EVENT), any.acceptedCardTypes)
        assertTrue(ScanRequest.player().instruction != "Scan a Game Card")
        assertTrue(ScanRequest.property().instruction != "Scan a Game Card")
        assertTrue(ScanRequest.event().instruction != "Scan a Game Card")
    }

    @Test
    fun multipleTypesListBoth() {
        assertEquals("Scan a Player or Property Card", ScanRequest.playerOrProperty().instruction)
    }

    @Test
    fun undoAuthorizationShowsRemainingCount() {
        assertEquals("Scan a Player Card — 2 players remaining", ScanRequest.undoAuthorization(2).instruction)
        assertEquals("Scan a Player Card — 1 player remaining", ScanRequest.undoAuthorization(1).instruction)
        assertEquals("Please scan a Player Card.", ScanRequest.undoAuthorization(2).mismatchInstruction)
    }

    @Test
    fun invalidCardDoesNotChangePendingRequest() {
        val deliverer = ScanResultDeliverer()
        val request = ScanRequest.property()
        deliverer.prepareConsumer(ScanResultConsumer.GAME, request)
        assertEquals("Scan a Property Card", deliverer.peekScanRequest()?.instruction)
        val validation = ScannerCardFilter.validate(
            CardResolution.Success(
                cardId = "USR_01",
                cardType = CardType.USER,
                displayName = "Car",
                qrPayload = "MUB:PL:CAR",
            ),
            request,
        )
        assertTrue(validation is CardTypeValidation.WrongType)
        assertEquals("Scan a Property Card", deliverer.peekScanRequest()?.instruction)
        assertEquals("Please scan a Property Card.", request.mismatchInstruction)
    }

    @Test
    fun cancellationClearsPendingScanRequest() {
        val deliverer = ScanResultDeliverer()
        deliverer.prepareConsumer(ScanResultConsumer.GAME, ScanRequest.player())
        deliverer.clearPendingScanRequest()
        assertNull(deliverer.peekScanRequest())
    }

    @Test
    fun recompositionDoesNotResetInstruction() {
        val request = ScanRequest.player()
        val state = GameUiState().withScanRequest(request)
        val recomposed = state.copy(loading = false, message = "ignored")
        assertEquals("Scan a Player Card", recomposed.scanRequest?.instruction)
        assertEquals(recomposed.scanRequest?.heading, recomposed.scanRequest?.overlayInstruction)
    }
}
