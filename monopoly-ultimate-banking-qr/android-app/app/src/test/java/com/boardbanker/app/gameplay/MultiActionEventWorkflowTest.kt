package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventTargetType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiActionEventWorkflowTest {
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun multiActionEventPlansSecondActionNotEngineRule() {
        val definitions = definitionsWithEvent(
            "EVT_UI_MULTI",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
            ),
        )
        val controller = GameplayWorkflowController(definitions)
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_UI_MULTI", session)
        val continueActions = controller.onEventContinue(session)
        assertTrue(continueActions.any { it is WorkflowAction.ExecuteCommand })
        val engine = DefaultGameEngine(definitions)
        val afterFirst = engine.process(
            session,
            GameCommand.ApplyEvent(eventId = "EVT_UI_MULTI", actingPlayerId = "USR_01"),
        ).session
        controller.onCommandSucceeded(WorkflowCommandContext.ApplyEvent("EVT_UI_MULTI"), afterFirst)
        val resume = controller.resumePendingEventExecution(afterFirst)
        assertTrue(resume.any { it is WorkflowAction.RequestScan })
        val second = controller.currentState() as GameplayWorkflowState.EventCollectingTargets
        assertEquals(1, second.actionIndex)
        assertEquals(1, second.plan.actionIndex)
        assertEquals("Scan a Player Card", resume.scanInstruction())
        assertEquals("Scan Player Card", EventWorkflowPlanner.scanHeaderForPlan(second.plan, second.plan.steps[second.stepIndex]))
    }

    @Test
    fun immediatePlayerScanImmediateSequence() {
        val definitions = definitionsWithEvent(
            "EVT_UI_SCAN",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
                action("PAY_PER_OWNED_PROPERTY"),
            ),
        )
        val controller = GameplayWorkflowController(definitions)
        val engine = DefaultGameEngine(definitions)
        var session = AppTestSupport.sessionWithProperty("PRP_01", "USR_01", 1)

        controller.onEventScanned("EVT_UI_SCAN", session)
        controller.onEventContinue(session)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_UI_SCAN", "USR_01")).session
        assertNotNull(session.pendingEventExecution)
        controller.onCommandSucceeded(WorkflowCommandContext.ApplyEvent("EVT_UI_SCAN"), session)
        handle(controller.resumePendingEventExecution(session))
        controller.onUserScanned("USR_02", session)
        session = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_UI_SCAN", "USR_01", targetPlayerId = "USR_02"),
        ).session
        assertNull(session.pendingEventExecution)
    }

    @Test
    fun processRecreationRestoresSecondActionPrompt() {
        val definitions = definitionsWithEvent(
            "EVT_UI_RESTORE",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
            ),
        )
        val engine = DefaultGameEngine(definitions)
        val pending = engine.process(
            AppTestSupport.newGame(),
            GameCommand.ApplyEvent("EVT_UI_RESTORE", "USR_01"),
        ).session
        val restored = serializer.deserialize(serializer.serialize(pending))
        val controller = GameplayWorkflowController(definitions)
        handle(controller.restoreWorkflowFromSession(restored))
        val collecting = controller.currentState() as GameplayWorkflowState.EventCollectingTargets
        assertEquals(1, collecting.actionIndex)
        assertEquals("Scan a Player Card", EventWorkflowPlanner.scanPrompt(collecting.plan.steps[collecting.stepIndex]))
        assertEquals("Scan Player Card", EventWorkflowPlanner.scanHeaderForPlan(collecting.plan, collecting.plan.steps[collecting.stepIndex]))
    }

    @Test
    fun duplicateScanDoesNotAdvanceStep() {
        val definitions = AppTestSupport.definitions
        val controller = GameplayWorkflowController(definitions)
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_05", session)
        controller.onEventContinue(session)
        val wrong = controller.onUserScanned("USR_01", session)
        assertTrue(wrong.any { it is WorkflowAction.WrongCardType })
        val collecting = controller.currentState() as GameplayWorkflowState.EventCollectingTargets
        assertEquals(EventScanStep.PROPERTY, collecting.plan.steps[collecting.stepIndex])
    }

    @Test
    fun mandatoryEventBlocksUnrelatedWorkflowReset() {
        val controller = GameplayWorkflowController(AppTestSupport.definitions)
        val session = AppTestSupport.newGame()
        controller.onEventScanned("EVT_05", session)
        controller.onEventContinue(session)
        assertTrue(controller.hasMandatoryEventActionPending())
    }

    @Test
    fun eventWorkflowPlannerUsesActionIndexNotOnlyEngineRule() {
        val definitions = definitionsWithEvent(
            "EVT_UI_INDEX",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
            ),
        )
        val event = definitions.events["EVT_UI_INDEX"]!!
        val firstPlan = EventWorkflowPlanner.planForEventAtAction(event, 0)
        val secondPlan = EventWorkflowPlanner.planForEventAtAction(event, 1)
        assertEquals(0, firstPlan.actionIndex)
        assertEquals(1, secondPlan.actionIndex)
        assertEquals(EventWorkflowPattern.EVENT_ONLY, firstPlan.pattern)
        assertEquals(EventWorkflowPattern.TWO_PLAYER_TARGET, secondPlan.pattern)
    }

    private fun definitionsWithEvent(eventId: String, actions: List<EventActionDefinition>): GameDefinitions {
        val uk = AppTestSupport.definitions
        val template = uk.events["EVT_13"]!!
        return uk.copy(
            events = uk.events + (
                eventId to EventDefinition(
                    eventId = eventId,
                    deckId = template.deckId,
                    name = "UI Multi Action Test",
                    qrPayload = "MUB:TEST:UI",
                    eventSubtitle = template.eventSubtitle,
                    eventDescription = template.eventDescription,
                    actions = actions,
                )
            ),
        )
    }

    private fun action(
        actionType: String,
        requiresPlayerScan: Boolean = false,
        targetType: String = EventTargetType.NONE.name,
    ): EventActionDefinition = EventActionDefinition(
        actionType = actionType,
        targetType = targetType,
        requiresPlayerScan = requiresPlayerScan,
    )

    private fun handle(actions: List<WorkflowAction>) {
        actions.forEach { action ->
            when (action) {
                is WorkflowAction.StateChanged -> Unit
                else -> Unit
            }
        }
    }
}

private fun List<WorkflowAction>.scanInstruction(): String =
    filterIsInstance<WorkflowAction.RequestScan>().single().request.scanRequest.instruction
