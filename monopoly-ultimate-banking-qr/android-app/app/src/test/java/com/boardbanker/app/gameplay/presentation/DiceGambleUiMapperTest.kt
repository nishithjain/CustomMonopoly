package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceGambleUiMapperTest {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)

    private fun sessionWithPendingGamble(): GameSession {
        var session = GameSession(
            gameId = "MAPPER_TEST",
            editionId = EditionIds.INDIA,
            editionDefinitionVersion = definitions.edition!!.definitionVersion,
        )
        session = engine.process(session, GameCommand.CreateGame("MAPPER_TEST")).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = engine.process(
                session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            ).session
        }
        session = engine.process(session, GameCommand.StartGame).session
        return engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
    }

    @Test
    fun mapsPendingGambleToUiState() {
        val session = sessionWithPendingGamble()
        val ui = DiceGambleUiMapper.map(session, definitions, commandInFlight = false)
        requireNotNull(ui)
        assertEquals("EVT_17", ui.eventId)
        assertEquals("Lucky Break", ui.eventName)
        assertEquals("USR_01", ui.playerId)
        assertEquals("Attempt 1 of 3", ui.attemptLabel)
        assertTrue(ui.rollEnabled)
    }

    @Test
    fun showsRemainingAttemptsAfterFailedRoll() {
        var session = sessionWithPendingGamble()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        val ui = DiceGambleUiMapper.map(session, definitions, commandInFlight = false)!!
        assertEquals("No doubles — 2 attempts remaining", ui.attemptLabel)
        assertEquals(1, ui.dieOne)
        assertEquals(2, ui.dieTwo)
        assertTrue(ui.rollEnabled)
    }

    @Test
    fun formatsJackpotAndPenaltyFromConfiguration() {
        val session = sessionWithPendingGamble()
        val ui = DiceGambleUiMapper.map(session, definitions, commandInFlight = false)!!
        assertTrue(ui.jackpotText.contains("15"))
        assertTrue(ui.penaltyText.contains("5"))
    }

    @Test
    fun rollDisabledWhileCommandInFlight() {
        val session = sessionWithPendingGamble()
        val ui = DiceGambleUiMapper.map(session, definitions, commandInFlight = true)!!
        assertFalse(ui.rollEnabled)
        assertEquals(DiceGambleStatus.ROLLING, ui.status)
    }
}
