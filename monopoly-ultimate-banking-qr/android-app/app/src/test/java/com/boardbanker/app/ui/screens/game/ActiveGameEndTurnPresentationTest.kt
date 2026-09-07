package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveGameEndTurnPresentationTest {
    @Test
    fun subtitleUsesPossessivePlayerName() {
        assertEquals("End Nishith's turn", ActiveGameEndTurnPresentation.subtitle("Nishith"))
        assertEquals("End James' turn", ActiveGameEndTurnPresentation.subtitle("James"))
    }

    @Test
    fun contentDescriptionUsesPlayerName() {
        assertEquals(
            "End Nishith's turn",
            ActiveGameEndTurnPresentation.contentDescription("Nishith"),
        )
    }

    @Test
    fun disabledReasonForLuckyDraw() {
        val reason = ActiveGameEndTurnPresentation.disabledReason(
            activePlayerName = "Nishith",
            actionAvailability = ActiveGameActionAvailability(
                scanCardEnabled = true,
                endTurnEnabled = false,
                bankActionsEnabled = true,
                getOutOfJailEnabled = false,
            ),
            workflowState = GameplayWorkflowState.EventDrawScanRequired(
                parentEventId = "EVT_15",
                actingPlayerId = "USR_01",
            ),
            hasPendingDiceGamble = false,
            hasPendingEventDraw = true,
            commandInFlight = false,
            gameplayLocked = false,
        )

        assertEquals(
            "Complete Lucky Draw before ending Nishith's turn.",
            reason,
        )
    }

    @Test
    fun disabledReasonNullWhenEndTurnEnabled() {
        val reason = ActiveGameEndTurnPresentation.disabledReason(
            activePlayerName = "Nishith",
            actionAvailability = ActiveGameActionAvailability(
                scanCardEnabled = true,
                endTurnEnabled = true,
                bankActionsEnabled = true,
                getOutOfJailEnabled = false,
            ),
            workflowState = GameplayWorkflowState.Ready,
            hasPendingDiceGamble = false,
            hasPendingEventDraw = false,
            commandInFlight = false,
            gameplayLocked = false,
        )

        assertNull(reason)
    }
}
