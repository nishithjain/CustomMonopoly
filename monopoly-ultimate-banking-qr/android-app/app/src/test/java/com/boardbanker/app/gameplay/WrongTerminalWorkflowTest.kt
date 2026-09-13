package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowController
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.workflow.WorkflowAction
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongTerminalWorkflowTest {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private val controller = GameplayWorkflowController(indiaDefinitions)

    @Test
    fun pendingEnergyGridLanding_rejectsPropertyAndEventScans() {
        val session = sessionAfterWrongTerminal()
        controller.beginPendingEnergyGridLanding(session)

        val propertyReject = controller.onPropertyScanned("PRP_01", session)
        assertTrue(propertyReject.any { it is WorkflowAction.WrongCardType })
        assertTrue(controller.currentState() is GameplayWorkflowState.WaitingForExpectedEnergyGridScan)

        val eventReject = controller.onEventScanned("EVT_01", session)
        assertTrue(eventReject.any { it is WorkflowAction.WrongCardType })

        val userReject = controller.onUserScanned("USR_02", session)
        assertTrue(userReject.any { it is WorkflowAction.WrongCardType })
    }

    @Test
    fun pendingEnergyGridLanding_rejectsPropertyScanBeforeWorkflowPrompt() {
        val session = sessionAfterWrongTerminal()

        val propertyReject = controller.onPropertyScanned("PRP_01", session)
        assertTrue(propertyReject.any { it is WorkflowAction.WrongCardType })

        val eventReject = controller.onEventScanned("EVT_01", session)
        assertTrue(eventReject.any { it is WorkflowAction.WrongCardType })
    }

    @Test
    fun pendingEnergyGridLanding_acceptsExpectedEnergyGridScan() {
        val session = sessionAfterWrongTerminal()
        controller.beginPendingEnergyGridLanding(session)

        val actions = controller.onEnergyGridScanned("ENG_02", session)
        assertTrue(
            actions.any {
                it is WorkflowAction.StateChanged ||
                    it is WorkflowAction.ExecuteCommand
            },
        )
        assertTrue(
            actions.none { it is WorkflowAction.WrongCardType },
        )
    }

    private fun sessionAfterWrongTerminal(): com.boardbanker.core.model.GameSession {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_21",
                actingPlayerId = "USR_01",
                fromBoardPosition = 28,
            ),
        ).session
        return session
    }
}
