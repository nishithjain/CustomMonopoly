package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.validation.PlayerNameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPlayerNameTests {
    private val engine = TestFixtures.engine

    @Test
    fun registrationStoresCustomNameAndStartingBalance() {
        var session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        session = result.session
        assertEquals("Nishith", session.players["USR_01"]!!.playerName)
        assertEquals(1500, session.players["USR_01"]!!.balance)
    }

    @Test
    fun maxLengthTenAccepted() {
        val session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "ABCDEFGHIJ"))
        assertTrue(result.isSuccess)
        assertEquals("ABCDEFGHIJ", result.session.players["USR_01"]!!.playerName)
    }

    @Test
    fun maxLengthElevenRejected() {
        val session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "ABCDEFGHIJK"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertTrue(result.error is GameError.PlayerNameTooLong)
    }

    @Test
    fun emptyNameRejected() {
        val session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "   "))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertTrue(result.error is GameError.InvalidPlayerName)
    }

    @Test
    fun nameTrimmedOnRegistration() {
        val session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_01", "  Alex  "))
        assertEquals("Alex", result.session.players["USR_01"]!!.playerName)
    }

    @Test
    fun duplicateCustomNamesAllowedForDifferentPlayers() {
        var session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Alex")).session
        val result = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Alex"))
        assertTrue(result.isSuccess)
        assertEquals("Alex", result.session.players["USR_01"]!!.playerName)
        assertEquals("Alex", result.session.players["USR_02"]!!.playerName)
    }

    @Test
    fun renameDuringSetupUpdatesNameWithoutChangingBalance() {
        var session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishi")).session
        val balance = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.RenamePlayer("USR_01", "Nishith"))
        assertTrue(result.isSuccess)
        assertEquals("Nishith", result.session.players["USR_01"]!!.playerName)
        assertEquals(balance, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun renameRejectedAfterGameStarts() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        assertEquals(GameStatus.ACTIVE, session.status)
        val result = engine.process(session, GameCommand.RenamePlayer("USR_01", "Updated"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertNotNull(result.error)
    }

    @Test
    fun customNamesSurviveSerializationRoundTrip() {
        var session = engine.process(GameSession(gameId = "G1"), GameCommand.CreateGame("G1")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Aditya")).session
        session = engine.process(session, GameCommand.StartGame).session
        val serializer = KotlinGameSessionSerializer()
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals("Nishith", restored.players["USR_01"]!!.playerName)
        assertEquals("Aditya", restored.players["USR_02"]!!.playerName)
    }

    @Test
    fun playerNameRulesMaxLengthConstantIsTen() {
        assertEquals(10, PlayerNameRules.MAX_LENGTH)
    }

    @Test
    fun legacySessionWithoutPlayerNameDeserializesWithEmptyName() {
        val serializer = KotlinGameSessionSerializer()
        var session = engine.process(GameSession(gameId = "LEGACY"), GameCommand.CreateGame("LEGACY")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Legacy")).session
        val json = serializer.serialize(session).replace("\"playerName\":\"Legacy\"", "\"playerName\":\"Legacy\"").let {
            // Simulate v1 JSON without playerName by removing the field from serialized output
            it.replace(",\"playerName\":\"Legacy\"", "")
        }
        val restored = serializer.deserialize(json)
        assertEquals("", restored.players["USR_01"]!!.playerName)
    }
}
