package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveGameActionAvailabilityTest {
    @Test
    fun jailedActivePlayer_disablesScanButAllowsEndTurn() {
        val availability = ActiveGameActionAvailability.forActivePlayer(
            activePlayerInJail = true,
            commandInFlight = false,
            gameplayLocked = false,
            workflowState = GameplayWorkflowState.Ready,
            hasMandatoryEventPending = false,
            hasPendingDiceGamble = false,
            hasPendingEventDraw = false,
        )
        assertFalse(availability.scanCardEnabled)
        assertTrue(availability.endTurnEnabled)
        assertTrue(availability.bankActionsEnabled)
        assertTrue(availability.getOutOfJailEnabled)
    }

    @Test
    fun freeActivePlayer_enablesNormalTurnActions() {
        val availability = ActiveGameActionAvailability.forActivePlayer(
            activePlayerInJail = false,
            commandInFlight = false,
            gameplayLocked = false,
            workflowState = GameplayWorkflowState.Ready,
            hasMandatoryEventPending = false,
            hasPendingDiceGamble = false,
            hasPendingEventDraw = false,
        )
        assertTrue(availability.scanCardEnabled)
        assertTrue(availability.endTurnEnabled)
        assertTrue(availability.bankActionsEnabled)
        assertFalse(availability.getOutOfJailEnabled)
    }

    @Test
    fun commandInFlight_disablesAllActions() {
        val availability = ActiveGameActionAvailability.forActivePlayer(
            activePlayerInJail = true,
            commandInFlight = true,
            gameplayLocked = false,
            workflowState = GameplayWorkflowState.Ready,
            hasMandatoryEventPending = false,
            hasPendingDiceGamble = false,
            hasPendingEventDraw = false,
        )
        assertFalse(availability.scanCardEnabled)
        assertFalse(availability.endTurnEnabled)
        assertFalse(availability.bankActionsEnabled)
        assertFalse(availability.getOutOfJailEnabled)
    }
}
