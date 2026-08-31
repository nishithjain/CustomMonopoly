package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.validation.DefinitionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupTests {
    private val engine = TestFixtures.engine
    private val definitions = TestFixtures.definitions

    @Test
    fun definitionsAreValid() {
        val problems = DefinitionValidator().validate(definitions)
        assertTrue("Validation problems: $problems", problems.isEmpty())
    }

    @Test
    fun tsSetup001_registerPlayersWithM1500() {
        var session = engine.process(
            TestFixtures.emptySession("G1"),
            GameCommand.CreateGame("G1"),
        ).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Aditya")).session
        val result = engine.process(session, GameCommand.StartGame)

        assertEquals(GameStatus.ACTIVE, result.session.status)
        assertEquals(definitions.bankingValues.startingBalance, result.session.players["USR_01"]!!.balance)
        assertEquals(definitions.bankingValues.startingBalance, result.session.players["USR_02"]!!.balance)
        assertEquals(1500, definitions.bankingValues.startingBalance)
        assertEquals("Nishith", result.session.players["USR_01"]!!.playerName)
        assertEquals("Aditya", result.session.players["USR_02"]!!.playerName)
        assertTrue(result.session.properties.values.all { it.ownerPlayerId == null })
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.GAME_START })
    }

    @Test
    fun tsSetup002_twoPlayerGameValid() {
        val session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        assertEquals(GameStatus.ACTIVE, session.status)
    }

    @Test
    fun tsSetup003_rejectDuplicatePlayer() {
        var session = engine.process(
            TestFixtures.emptySession("G1"),
            GameCommand.CreateGame("G1"),
        ).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Duplicate"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertNotNull(result.error)
    }
}
