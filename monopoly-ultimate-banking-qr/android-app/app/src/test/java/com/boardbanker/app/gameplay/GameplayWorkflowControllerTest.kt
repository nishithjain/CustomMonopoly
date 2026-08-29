package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
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

    @Test
    fun boomTownScanStepsArePlayerThenProperty() {
        val rule = definitions.events["EVT_01"]!!.engineRule
        val plan = EventWorkflowPlanner.plan("EVT_01", rule)
        assertEquals(EventWorkflowPattern.MOVE_THEN_PROPERTY_CHOICE, plan.pattern)
        assertEquals(
            listOf(EventScanStep.ACTING_PLAYER, EventScanStep.PROPERTY),
            plan.steps,
        )
        assertEquals("Scan a Player Card", EventWorkflowPlanner.scanRequest(plan.steps[0]).instruction)
        assertEquals("Scan a Property Card", EventWorkflowPlanner.scanRequest(plan.steps[1]).instruction)
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
        val actions = controller.onBuySelected(session)
        assertTrue(actions.any { it is WorkflowAction.RequestScan })
        assertTrue(controller.currentState() is GameplayWorkflowState.WaitingForPurchasingPlayer)
    }

    @Test
    fun locationBuyUsesKnownLandingPlayerWithoutScan() {
        val session = AppTestSupport.newGame()
        val actions = controller.beginLocationDestinationProperty("USR_01", "PRP_01", session)
        assertTrue(controller.currentState() is GameplayWorkflowState.UnownedPropertyDecision)
        val buyActions = controller.onBuySelected(session)
        assertTrue(buyActions.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(buyActions.none { it is WorkflowAction.RequestScan })
    }

    @Test
    fun duplicateBuyTapIgnored() {
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        controller.onBuySelected(session)
        val second = controller.onBuySelected(session)
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

    @Test
    fun boomTownFirstScanIsPlayerCard() {
        controller.onEventScanned("EVT_01")
        val actions = controller.onEventContinue()
        assertEquals("Scan a Player Card", actions.scanInstruction())
        assertEquals(setOf(com.boardbanker.core.card.CardType.USER), actions.scanRequest().acceptedCardTypes)
    }

    @Test
    fun boomTownAfterPlayerScanRequestsPropertyCard() {
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_01")
        controller.onEventContinue()
        val actions = controller.onUserScanned("USR_01", session)
        assertEquals("Scan a Property Card", actions.scanInstruction())
        assertEquals(setOf(com.boardbanker.core.card.CardType.PROPERTY), actions.scanRequest().acceptedCardTypes)
    }

    @Test
    fun grandDesignsUpdatesInstructionAfterPlayerScan() {
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_05")
        assertEquals("Scan a Player Card", controller.onEventContinue().scanInstruction())
        assertEquals("Scan a Property Card", controller.onUserScanned("USR_01", session).scanInstruction())
    }

    @Test
    fun hauntedHouseUpdatesInstructionAfterEachScan() {
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_06")
        assertEquals("Scan a Player Card", controller.onEventContinue().scanInstruction())
        assertEquals("Scan a Player Card", controller.onUserScanned("USR_01", session).scanInstruction())
        assertEquals("Scan a Property Card", controller.onUserScanned("USR_02", session).scanInstruction())
        assertEquals("Scan a Property Card", controller.onEventPropertyScanned("PRP_01").scanInstruction())
        val afterSecondProperty = controller.onEventPropertyScanned("PRP_02")
        assertTrue(afterSecondProperty.none { it is WorkflowAction.RequestScan })
        assertTrue(controller.currentState() is GameplayWorkflowState.EventConfirm)
    }

    @Test
    fun wrongCardDuringBoomTownKeepsPlayerInstruction() {
        controller.onEventScanned("EVT_01")
        controller.onEventContinue()
        val wrong = controller.onEventPropertyScanned("PRP_01")
        assertTrue(wrong.any { it is WorkflowAction.WrongCardType })
        val collecting = controller.currentState() as GameplayWorkflowState.EventCollectingTargets
        assertEquals(
            "Scan a Player Card",
            EventWorkflowPlanner.scanRequest(collecting.plan.steps[collecting.stepIndex]).instruction,
        )
    }
}

private fun List<WorkflowAction>.scanInstruction(): String = scanRequest().instruction

private fun List<WorkflowAction>.scanRequest() =
    filterIsInstance<WorkflowAction.RequestScan>().single().request.scanRequest
