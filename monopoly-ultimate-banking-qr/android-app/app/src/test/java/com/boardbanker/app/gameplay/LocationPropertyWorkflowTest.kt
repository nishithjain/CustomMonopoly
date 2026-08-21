package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.location.LocationWorkflowConstants
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowController
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.workflow.WorkflowAction
import com.boardbanker.core.command.GameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPropertyWorkflowTest {
    private val definitions = AppTestSupport.definitions
    private val engine = AppTestSupport.engine
    private val controller = GameplayWorkflowController(definitions)

    @Test
    fun locationFeeOnly_deductsWithoutPropertyLanding() {
        var session = AppTestSupport.newGame()
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(
            session,
            GameCommand.PayLocationFee("USR_01", LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID),
        )
        assertEquals(before - definitions.bankingValues.locationFee, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.any { it.transactionType.name == "LOCATION_FEE" })
    }

    @Test
    fun locationDestination_unownedPropertyHandsOffToBuyWithoutPlayerScan() {
        val session = AppTestSupport.newGame()
        val actions = controller.beginLocationDestinationProperty("USR_01", "PRP_01", session)
        assertTrue(actions.any { it is WorkflowAction.StateChanged })
        assertTrue(controller.currentState() is GameplayWorkflowState.UnownedPropertyDecision)
        val buy = controller.onBuySelected(session)
        assertTrue(buy.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(buy.none { it is WorkflowAction.RequestScan })
    }

    @Test
    fun locationDestination_ownPropertyExecutesLandingImmediately() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val actions = controller.beginLocationDestinationProperty("USR_01", "PRP_01", session)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is WorkflowAction.ExecuteCommand)
    }

    @Test
    fun locationDestination_opponentPropertyExecutesRentLandingImmediately() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_02", "PRP_01")).session
        val actions = controller.beginLocationDestinationProperty("USR_01", "PRP_01", session)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is WorkflowAction.ExecuteCommand)
    }

    @Test
    fun locationWaiting_rejectsUserCard() {
        controller.enterLocationWaitingForDestination("USR_01")
        val actions = controller.onUserScanned("USR_01", AppTestSupport.newGame())
        assertTrue(actions.any { it is WorkflowAction.WrongCardType })
    }
}
