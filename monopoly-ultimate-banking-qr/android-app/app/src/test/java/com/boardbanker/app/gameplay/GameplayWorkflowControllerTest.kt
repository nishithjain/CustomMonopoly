package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EventEngineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventWorkflowPlannerTest {
  private val definitions = AppTestSupport.definitions

    @Test
    fun all23EventsHaveWorkflowPatterns() {
        val eventIds = (1..23).map { "EVT_${it.toString().padStart(2, '0')}" }
        val coverage = EventWorkflowPlanner.coverageForAllEvents(eventIds, definitions.events.mapValues { it.value.engineRule })
        assertEquals(23, coverage.size)
    }

    @Test
    fun evt06UsesTwoPlayerTwoPropertyPattern() {
        val rule = definitions.events["EVT_06"]!!.engineRule
        assertEquals(EventWorkflowPattern.TWO_PLAYER_TWO_PROPERTY, EventWorkflowPlanner.classify(rule))
    }

    @Test
    fun evt13IsEventOnly() {
        val rule = definitions.events["EVT_13"]!!.engineRule
        assertEquals(EventWorkflowPattern.EVENT_ONLY, EventWorkflowPlanner.classify(rule))
    }
}

class GameplayWorkflowControllerTest {
    private val definitions = AppTestSupport.definitions
    private val controller = GameplayWorkflowController(definitions)

    @Test
    fun unownedPropertyShowsBuyDecision() {
        val session = AppTestSupport.newGame()
        val actions = controller.onPropertyScanned("PRP_01", session)
        assertTrue(actions.any { it is WorkflowAction.StateChanged })
        assertTrue(controller.currentState() is GameplayWorkflowState.UnownedPropertyDecision)
    }

    @Test
    fun buyRequiresPlayerScan() {
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        val actions = controller.onBuySelected()
        assertTrue(actions.any { it is WorkflowAction.RequestScan })
        assertTrue(controller.currentState() is GameplayWorkflowState.WaitingForPurchasingPlayer)
    }

    @Test
    fun duplicateBuyTapIgnored() {
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        controller.onBuySelected()
        val second = controller.onBuySelected()
        assertTrue(second.isEmpty())
    }

    @Test
    fun ownedPropertyRequestsRentPayerWithoutAutoScan() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        val actions = controller.onPropertyScanned("PRP_01", session)
        assertTrue(controller.currentState() is GameplayWorkflowState.WaitingForRentPayer)
        assertTrue(actions.none { it is WorkflowAction.RequestScan })
    }

    @Test
    fun cancelResetsWorkflow() {
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        controller.onCancel()
        assertTrue(controller.currentState() is GameplayWorkflowState.Ready)
    }

    @Test
    fun eventScanShowsIntroBeforeTargetCollection() {
        controller.onEventScanned("EVT_05")
        assertTrue(controller.currentState() is GameplayWorkflowState.EventIntro)
    }

    @Test
    fun eventContinueStartsTargetCollection() {
        controller.onEventScanned("EVT_05")
        val actions = controller.onEventContinue()
        assertTrue(actions.any { it is WorkflowAction.RequestScan })
        assertTrue(controller.currentState() is GameplayWorkflowState.EventCollectingTargets)
    }

    @Test
    fun eventScanStartsTargetCollection() {
        controller.onEventScanned("EVT_05")
        controller.onEventContinue()
        assertTrue(controller.currentState() is GameplayWorkflowState.EventCollectingTargets)
    }
}
