package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.DiceGambleMode
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType
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
    fun mapsPendingGambleToModeSelection() {
        val session = sessionWithPendingGamble()
        val ui = DiceGambleUiMapper.map(session, definitions, rollInProgress = false)
        requireNotNull(ui)
        assertEquals("EVT_17", ui.eventId)
        assertEquals("Lucky Break", ui.eventName)
        assertEquals("USR_01", ui.playerId)
        assertEquals(DiceGambleStatus.SELECT_MODE, ui.status)
        assertEquals(null, ui.mode)
        assertTrue(ui.rollEnabled)
    }

    @Test
    fun mapsInAppModeToRollState() {
        var session = sessionWithPendingGamble()
        session = AppTestSupport.selectDiceGambleMode(session, DiceGambleMode.IN_APP, engine)
        val ui = DiceGambleUiMapper.map(session, definitions, rollInProgress = false)!!
        assertEquals(DiceGambleMode.IN_APP, ui.mode)
        assertEquals("Attempt 1 of 3", ui.attemptLabel)
        assertEquals("Roll Dice", ui.rollButtonLabel)
        assertTrue(ui.rollEnabled)
    }

    @Test
    fun showsRemainingAttemptsAfterFailedRoll() {
        val rollingEngine = DefaultGameEngine(definitions, SequenceDiceRoller(1 to 2))
        var session = sessionWithPendingGamble()
        session = AppTestSupport.selectDiceGambleMode(session, DiceGambleMode.IN_APP, rollingEngine)
        session = rollingEngine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val ui = DiceGambleUiMapper.map(session, definitions, rollInProgress = false)!!
        assertEquals("No doubles — 2 attempts remaining", ui.attemptLabel)
        assertEquals(1, ui.dieOne)
        assertEquals(2, ui.dieTwo)
        assertEquals("Roll Again", ui.rollButtonLabel)
        assertTrue(ui.rollEnabled)
    }

    @Test
    fun formatsJackpotAndPenaltyFromConfiguration() {
        val session = sessionWithPendingGamble()
        val ui = DiceGambleUiMapper.map(session, definitions, rollInProgress = false)!!
        assertTrue(ui.jackpotText.contains("15"))
        assertTrue(ui.penaltyText.contains("5"))
    }

    @Test
    fun rollDisabledWhileRollInProgress() {
        var session = sessionWithPendingGamble()
        session = AppTestSupport.selectDiceGambleMode(session, DiceGambleMode.IN_APP, engine)
        val ui = DiceGambleUiMapper.map(session, definitions, rollInProgress = true)!!
        assertFalse(ui.rollEnabled)
        assertEquals(DiceGambleStatus.ROLLING, ui.status)
        assertEquals("Rolling...", ui.rollButtonLabel)
    }

    @Test
    fun completedOutcomeShowsDiceAndContinue() {
        val session = sessionWithPendingGamble()
        val completed = DiceGambleUiMapper.buildCompletedOutcome(
            session = session,
            definitions = definitions,
            eventId = "EVT_17",
            actingPlayerId = "USR_01",
            dieOne = 6,
            dieTwo = 6,
            transactions = listOf(
                com.boardbanker.core.model.Transaction(
                    transactionId = "TX_1",
                    gameId = session.gameId,
                    timestamp = 1L,
                    transactionType = TransactionType.BANK_CREDIT,
                    amount = 15000,
                    playerId = "USR_01",
                ),
            ),
            jackpotAmount = 15000,
            penaltyAmount = 5000,
        )
        val ui = DiceGambleUiMapper.map(
            session = session,
            definitions = definitions,
            rollInProgress = false,
            completedOutcome = completed,
        )!!
        assertTrue(ui.showContinue)
        assertEquals("Doubles!", ui.outcomeHeadline)
        assertEquals(6, ui.dieOne)
        assertEquals(6, ui.dieTwo)
        assertFalse(ui.rollEnabled)
    }
}
