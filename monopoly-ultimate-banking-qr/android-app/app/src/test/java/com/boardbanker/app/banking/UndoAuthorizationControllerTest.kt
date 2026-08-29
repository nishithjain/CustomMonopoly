package com.boardbanker.app.banking

import com.boardbanker.core.card.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoAuthorizationControllerTest {

    private fun controllerWithTwoPlayers(): UndoAuthorizationController {
        val controller = UndoAuthorizationController()
        controller.begin(
            players = listOf(
                UndoAuthorizationPlayer("USR_01", "Arun", verified = false),
                UndoAuthorizationPlayer("USR_02", "Priya", verified = false),
                UndoAuthorizationPlayer("USR_03", "Rahul", verified = false),
                UndoAuthorizationPlayer("USR_04", "Sneha", verified = false),
            ),
            undoDescription = "GO salary",
        )
        return controller
    }

    @Test
    fun beginShowsEveryPlayerWaiting() {
        val controller = controllerWithTwoPlayers()
        val state = controller.snapshot()
        assertEquals(UndoAuthorizationPhase.COLLECTING, state.phase)
        assertEquals(0, state.verifiedCount)
        assertEquals(4, state.totalCount)
        assertEquals(listOf("Arun", "Priya", "Rahul", "Sneha"), state.waitingPlayers.map { it.displayName })
        assertTrue(state.players.none { it.verified })
    }

    @Test
    fun validPlayerBecomesVerifiedAndCountUpdates() {
        val controller = controllerWithTwoPlayers()
        val result = controller.onScan(CardType.USER, "USR_01") as UndoAuthorizationScanResult.PlayerVerified
        assertFalse(result.readyToUndo)
        assertEquals(1, result.state.verifiedCount)
        assertEquals("Players verified: 1 of 4", UndoAuthorizationController.progressLabel(1, 4))
        assertEquals(listOf("Priya", "Rahul", "Sneha"), result.state.waitingPlayers.map { it.displayName })
        assertTrue(result.state.players.first { it.playerId == "USR_01" }.verified)
    }

    @Test
    fun duplicateScanDoesNotIncreaseCount() {
        val controller = controllerWithTwoPlayers()
        controller.onScan(CardType.USER, "USR_01")
        val duplicate = controller.onScan(CardType.USER, "USR_01") as UndoAuthorizationScanResult.AlreadyApproved
        assertEquals("Arun", duplicate.playerName)
        assertEquals("Arun has already approved the undo.", duplicate.state.scanMessage)
        assertEquals(1, duplicate.state.verifiedCount)
        assertEquals(UndoAuthorizationPhase.COLLECTING, duplicate.state.phase)
    }

    @Test
    fun propertyAndEventCardsAreRejected() {
        val controller = controllerWithTwoPlayers()
        val property = controller.onScan(CardType.PROPERTY, "PRP_01") as UndoAuthorizationScanResult.WrongCard
        val event = controller.onScan(CardType.EVENT, "EVT_01") as UndoAuthorizationScanResult.WrongCard
        assertEquals(UndoAuthorizationController.WRONG_CARD_MESSAGE, property.state.scanMessage)
        assertEquals(UndoAuthorizationController.WRONG_CARD_MESSAGE, event.state.scanMessage)
        assertEquals(0, property.state.verifiedCount)
        assertEquals(0, event.state.verifiedCount)
    }

    @Test
    fun unknownAndUnregisteredPlayerCardsAreRejected() {
        val controller = controllerWithTwoPlayers()
        val unregistered = controller.onScan(CardType.USER, "USR_99") as UndoAuthorizationScanResult.UnregisteredPlayer
        assertEquals(UndoAuthorizationController.UNREGISTERED_PLAYER_MESSAGE, unregistered.state.scanMessage)
        assertEquals(0, unregistered.state.verifiedCount)
    }

    @Test
    fun undoIsNotReadyBeforeEveryPlayerApproves() {
        val controller = controllerWithTwoPlayers()
        val first = controller.onScan(CardType.USER, "USR_01") as UndoAuthorizationScanResult.PlayerVerified
        val second = controller.onScan(CardType.USER, "USR_02") as UndoAuthorizationScanResult.PlayerVerified
        val third = controller.onScan(CardType.USER, "USR_03") as UndoAuthorizationScanResult.PlayerVerified
        assertFalse(first.readyToUndo)
        assertFalse(second.readyToUndo)
        assertFalse(third.readyToUndo)
        assertEquals(UndoAuthorizationPhase.COLLECTING, third.state.phase)
    }

    @Test
    fun finalScanIsReadyToUndoExactlyOnce() {
        val controller = controllerWithTwoPlayers()
        controller.onScan(CardType.USER, "USR_01")
        controller.onScan(CardType.USER, "USR_02")
        controller.onScan(CardType.USER, "USR_03")
        val finalScan = controller.onScan(CardType.USER, "USR_04") as UndoAuthorizationScanResult.PlayerVerified
        assertTrue(finalScan.readyToUndo)
        assertEquals(UndoAuthorizationPhase.COMPLETING, finalScan.state.phase)

        val duplicateFinal = controller.onScan(CardType.USER, "USR_04")
        assertTrue(duplicateFinal is UndoAuthorizationScanResult.Ignored)
        val anotherPlayer = controller.onScan(CardType.USER, "USR_01")
        assertTrue(anotherPlayer is UndoAuthorizationScanResult.Ignored)
    }

    @Test
    fun cancelClearsVerificationWithoutChangingPhaseToCompleting() {
        val controller = controllerWithTwoPlayers()
        controller.onScan(CardType.USER, "USR_01")
        val cancelled = controller.cancel()
        assertEquals(UndoAuthorizationPhase.IDLE, cancelled.phase)
        assertTrue(cancelled.players.isEmpty())
        assertEquals(0, cancelled.verifiedCount)
        assertFalse(cancelled.active)
    }
}
