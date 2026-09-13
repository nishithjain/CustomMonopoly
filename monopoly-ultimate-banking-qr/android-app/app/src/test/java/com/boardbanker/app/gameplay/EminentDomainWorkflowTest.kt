package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EminentDomainWorkflowTest {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val controller = GameplayWorkflowController(definitions)
    private val engine = DefaultGameEngine(definitions)

    @Test
    fun uniqueLowestPropertySkipsPropertyScanAndAppliesImmediately() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        controller.onEventScanned("EVT_19", session)
        val actions = controller.onEventContinue(session)
        assertTrue(actions.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(actions.none { it is WorkflowAction.RequestScan })
    }

    @Test
    fun tiedLowestPropertiesRequirePropertyScan() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01", "PRP_02" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        controller.onEventScanned("EVT_19", session)
        val actions = controller.onEventContinue(session)
        assertTrue(actions.any { it is WorkflowAction.RequestScan })
        assertEquals(
            "Scan one of your lowest-value Property Cards",
            actions.scanInstruction(),
        )
    }

    @Test
    fun tiedLowestPropertiesRejectHigherValueScan() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01", "PRP_02" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        controller.onEventScanned("EVT_19", session)
        controller.onEventContinue(session)
        val wrong = controller.onEventPropertyScanned("PRP_12", session)
        assertTrue(wrong.any { it is WorkflowAction.WrongCardType })
    }

    @Test
    fun engineAutoSelectsUniqueLowestWithoutPropertyScan() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_19", "USR_01"))
        assertNull(result.session.properties["PRP_01"]!!.ownerPlayerId)
        assertEquals("USR_01", result.session.properties["PRP_12"]!!.ownerPlayerId)
    }
}

private fun List<WorkflowAction>.scanInstruction(): String =
    filterIsInstance<WorkflowAction.RequestScan>().single().request.scanRequest.instruction
