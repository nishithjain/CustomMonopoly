package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.EventWorkflowPattern
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowController
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.workflow.WorkflowAction
import com.boardbanker.app.ui.screens.game.ActiveGameCardPresentationBuilder
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HospitalExpenseWorkflowTest {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private val controller = GameplayWorkflowController(indiaDefinitions)

    @Test
    fun eventIntro_resolvesHospitalExpenseInstruction() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000))
        controller.onEventScanned("EVT_05", session)
        val intro = controller.currentState() as GameplayWorkflowState.EventIntro
        val expected = "Pay ${MoneyFormatter.format(10000, indiaDefinitions)} to the bank."
        assertEquals(expected, intro.eventDescription)
        assertFalse(intro.eventDescription.contains("{amount}"))

        val presentation = ActiveGameCardPresentationBuilder.build(intro, indiaDefinitions, session)
        assertTrue(presentation!!.body.contains(expected))
        assertFalse(presentation.body.contains("{amount}"))
    }

    @Test
    fun continue_appliesHospitalExpenseWithoutPlayerScan() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000))
        val before = session.players["USR_01"]!!.balance

        controller.onEventScanned("EVT_05", session)
        val actions = controller.onEventContinue(session)

        assertTrue(actions.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(actions.none { it is WorkflowAction.RequestScan })

        val command = actions.filterIsInstance<WorkflowAction.ExecuteCommand>().single().request.command
        assertTrue(command is GameCommand.ApplyEvent)
        assertEquals("EVT_05", (command as GameCommand.ApplyEvent).eventId)
        assertEquals("USR_01", command.actingPlayerId)

        val result = indiaEngine.process(session, command)
        assertEquals(before - 10000, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun repeatedContinue_doesNotQueueDuplicateCommands() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000))
        controller.onEventScanned("EVT_05", session)
        val first = controller.onEventContinue(session)
        val second = controller.onEventContinue(session)
        assertTrue(first.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(second.isEmpty())
    }

    @Test
    fun cancel_doesNotApplyHospitalExpense() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000))
        controller.onEventScanned("EVT_05", session)
        controller.onCancel()
        assertTrue(controller.currentState() is GameplayWorkflowState.Ready)
        assertEquals(
            EventWorkflowPattern.EVENT_ONLY,
            com.boardbanker.app.gameplay.workflow.EventWorkflowPlanner
                .planForEventAtAction(indiaDefinitions.events["EVT_05"]!!, 0)
                .pattern,
        )
    }

    @Test
    fun buyProperty_usesActivePlayerWithoutScan() {
        val session = indiaGame()
        controller.onPropertyScanned("PRP_01", session)
        val actions = controller.onBuySelected(session)
        assertTrue(actions.any { it is WorkflowAction.ExecuteCommand })
        assertTrue(actions.none { it is WorkflowAction.RequestScan })
    }

    private fun indiaGame(
        balances: Map<String, Int>? = null,
    ): GameSession {
        var result = indiaEngine.process(
            GameSession(
                gameId = "INDIA_TEST",
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = indiaDefinitions.edition!!.definitionVersion,
            ),
            GameCommand.CreateGame("INDIA_TEST"),
        )
        for (playerId in listOf("USR_01", "USR_02")) {
            result = indiaEngine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            )
        }
        result = indiaEngine.process(result.session, GameCommand.StartGame)
        var session = result.session
        if (balances != null) {
            session = session.copy(
                players = session.players.mapValues { (id, player) ->
                    balances[id]?.let { player.copy(balance = it) } ?: player
                },
            )
        }
        return session
    }
}
