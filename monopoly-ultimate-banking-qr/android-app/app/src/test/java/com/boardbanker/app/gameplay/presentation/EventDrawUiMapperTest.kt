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

class EventDrawUiMapperTest {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)

    private fun sessionWithPendingDraw(): GameSession {
        var session = GameSession(
            gameId = "DRAW_MAPPER_TEST",
            editionId = EditionIds.INDIA,
            editionDefinitionVersion = definitions.edition!!.definitionVersion,
        )
        session = engine.process(session, GameCommand.CreateGame("DRAW_MAPPER_TEST")).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = engine.process(
                session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            ).session
        }
        session = engine.process(session, GameCommand.StartGame).session
        return engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
    }

    @Test
    fun mapsPendingDrawToUiState() {
        val session = sessionWithPendingDraw()
        val ui = EventDrawUiMapper.map(session, definitions, commandInFlight = false)
        requireNotNull(ui)
        assertEquals("EVT_15", ui.parentEventId)
        assertEquals("Lucky Draw", ui.parentEventName)
        assertEquals(EventDrawUiMapper.INSTRUCTION, ui.instruction)
        assertEquals("Additional draw 1 of 3", ui.chainProgressText)
        assertTrue(ui.scanEnabled)
    }

    @Test
    fun scanDisabledWhileCommandInFlight() {
        val session = sessionWithPendingDraw()
        val ui = EventDrawUiMapper.map(session, definitions, commandInFlight = true)!!
        assertFalse(ui.scanEnabled)
    }
}
