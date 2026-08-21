package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveGameCardUiPolicyTest {
    @Test
    fun unownedPropertyShowsBuyAuctionCancel() {
        val visibility = ActiveGameCardUiPolicy.actionVisibility(
            workflowState = GameplayWorkflowState.UnownedPropertyDecision("PRP_01"),
            result = null,
            gameplayLocked = false,
        )
        assertTrue(visibility.showBuy)
        assertTrue(visibility.showAuction)
        assertTrue(visibility.showCancel)
        assertFalse(visibility.showContinue)
    }

    @Test
    fun unownedPropertyUsesPropertyCardId() {
        val cardId = ActiveGameCardUiPolicy.displayCardId(
            workflowState = GameplayWorkflowState.UnownedPropertyDecision("PRP_01"),
            result = null,
        )
        assertEquals("PRP_01", cardId)
    }

    @Test
    fun ownedPropertyHidesBuyAndAuction() {
        val visibility = ActiveGameCardUiPolicy.actionVisibility(
            workflowState = GameplayWorkflowState.WaitingForRentPayer(
                propertyId = "PRP_01",
                ownerPlayerId = "USR_01",
                ownerName = "Car",
            ),
            result = null,
            gameplayLocked = false,
        )
        assertFalse(visibility.showBuy)
        assertFalse(visibility.showAuction)
        assertTrue(visibility.showScanPlayer)
        assertTrue(visibility.showCancel)
    }

    @Test
    fun eventIntroShowsContinueAndCancel() {
        val visibility = ActiveGameCardUiPolicy.actionVisibility(
            workflowState = GameplayWorkflowState.EventIntro(
                eventId = "EVT_06",
                eventName = "Haunted House",
                eventSubtitle = "Something strange is going on!",
                eventDescription = "Swap properties.",
            ),
            result = null,
            gameplayLocked = false,
        )
        assertTrue(visibility.showContinue)
        assertTrue(visibility.showCancel)
        assertFalse(visibility.showBuy)
        assertFalse(visibility.showAuction)
    }

    @Test
    fun eventUsesEventCardId() {
        val cardId = ActiveGameCardUiPolicy.displayCardId(
            workflowState = GameplayWorkflowState.EventIntro(
                eventId = "EVT_06",
                eventName = "Haunted House",
                eventSubtitle = "Something strange is going on!",
                eventDescription = "Swap properties.",
            ),
            result = null,
        )
        assertEquals("EVT_06", cardId)
    }

    @Test
    fun userCardShowsDoneOnly() {
        val visibility = ActiveGameCardUiPolicy.actionVisibility(
            workflowState = GameplayWorkflowState.PlayerInfo("USR_01"),
            result = GameplayResultUiModel(
                displayCardId = "USR_01",
                title = "PLAYER",
                primaryMessage = "Car",
            ),
            gameplayLocked = false,
        )
        assertTrue(visibility.showDone)
        assertFalse(visibility.showBuy)
        assertFalse(visibility.showAuction)
    }

    @Test
    fun readyStateHasNoDisplayCard() {
        assertNull(
            ActiveGameCardUiPolicy.displayCardId(
                workflowState = GameplayWorkflowState.Ready,
                result = null,
            ),
        )
    }
}
