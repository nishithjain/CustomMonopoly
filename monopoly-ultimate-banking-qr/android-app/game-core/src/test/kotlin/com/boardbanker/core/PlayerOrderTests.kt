package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PlayerOrder
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import com.boardbanker.core.persistence.RawSavedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerOrderTests {
    private val engine = TestFixtures.engine
    private val serializer = KotlinGameSessionSerializer()

    private val scanOrder = listOf("USR_03", "USR_04", "USR_02", "USR_01")
    private val scanNames = mapOf(
        "USR_03" to "Player A",
        "USR_04" to "Player B",
        "USR_02" to "Player C",
        "USR_01" to "Player D",
    )

    @Before
    fun setUp() {
        scanNames.forEach { (playerId, name) ->
            require(TestFixtures.definitions.players.containsKey(playerId))
        }
    }

    @Test
    fun scanningPlayersPreservesDisplayOrder() {
        val session = registerPlayers(scanOrder)
        assertEquals(scanOrder, PlayerOrder.displayOrder(session))
        assertEquals(scanOrder, session.playerJoinOrder)
    }

    @Test
    fun firstScannedPlayerReceivesFirstTurn() {
        val session = startGame(scanOrder)
        assertEquals("USR_03", session.turnState!!.activePlayerId)
        assertEquals(scanOrder, session.turnState!!.turnOrder)
    }

    @Test
    fun startGameDoesNotAdvanceToSecondPlayer() {
        val session = startGame(scanOrder)
        assertEquals("USR_03", session.turnState!!.activePlayerId)
        assertFalse(
            session.transactions.any {
                it.transactionType == TransactionType.TURN_ADVANCED
            },
        )
    }

    @Test
    fun turnsRotateInScanOrder() {
        var session = startGame(scanOrder)
        val expectedRotation = listOf("USR_04", "USR_02", "USR_01", "USR_03")
        for (expectedActive in expectedRotation) {
            session = TestFixtures.endTurn(session, engine = engine).session
            assertEquals(expectedActive, session.turnState!!.activePlayerId)
        }
    }

    @Test
    fun skippedTurnsPreserveScanOrder() {
        var session = startGame(scanOrder)
        session = TestFixtures.withPendingSkip(session, "USR_04")
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        assertEquals(scanOrder, session.turnState!!.turnOrder)
    }

    @Test
    fun extraTurnsPreserveScanOrder() {
        var session = startGame(scanOrder)
        session = TestFixtures.withPendingExtra(session, "USR_03")
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals("USR_03", session.turnState!!.activePlayerId)
        assertEquals(TurnKind.EXTRA, session.turnState!!.turnKind)
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals("USR_04", session.turnState!!.activePlayerId)
        assertEquals(scanOrder, session.turnState!!.turnOrder)
    }

    @Test
    fun jailTransitionsPreserveScanOrder() {
        var session = startGame(scanOrder)
        val jailedPlayer = session.players["USR_04"]!!.copy(jailStatus = true)
        session = session.copy(players = session.players + ("USR_04" to jailedPlayer))
        session = TestFixtures.endTurn(session, "USR_03", engine = engine).session
        assertEquals("USR_04", session.turnState!!.activePlayerId)
        assertEquals(scanOrder, session.turnState!!.turnOrder)
    }

    @Test
    fun autosaveAndResumePreservePlayerOrder() {
        val session = startGame(scanOrder)
        val json = serializer.serialize(session)
        val restored = serializer.deserialize(json)
        assertEquals(scanOrder, restored.playerJoinOrder)
        assertEquals(scanOrder, restored.turnState!!.turnOrder)
        assertEquals("USR_03", restored.turnState!!.activePlayerId)
    }

    @Test
    fun undoRestoresActivePlayerAndTurnOrder() {
        var session = startGame(scanOrder)
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals("USR_04", session.turnState!!.activePlayerId)
        val undone = engine.process(session, GameCommand.UndoLastAction)
        assertTrue(undone.isSuccess)
        assertEquals("USR_03", undone.session.turnState!!.activePlayerId)
        assertEquals(scanOrder, undone.session.turnState!!.turnOrder)
        assertEquals(scanOrder, undone.session.playerJoinOrder)
    }

    @Test
    fun legacySaveWithoutJoinOrderDerivesDeterministicOrder() {
        val session = startGame(scanOrder).copy(playerJoinOrder = emptyList())
        val json = serializer.serialize(session)
        val legacyJson = json.replace("\"playerJoinOrder\":[\"USR_03\",\"USR_04\",\"USR_02\",\"USR_01\"],", "")
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = serializer,
            editionLoader = { TestFixtures.loadEdition(it) },
            manifestLoader = { TestFixtures.loadEdition(it).edition!! },
        )
        val result = orchestrator.restore(
            RawSavedGame(
                sessionJson = legacyJson,
                schemaVersion = 2,
            ),
        )
        assertTrue(result is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (result as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(scanOrder, restored.playerJoinOrder)
        assertEquals(scanOrder, restored.turnState!!.turnOrder)
    }

    @Test
    fun setupPhaseTracksJoinOrderBeforeStart() {
        val session = registerPlayers(scanOrder)
        assertEquals(GameStatus.SETUP, session.status)
        assertEquals(scanOrder, PlayerOrder.displayOrder(session))
    }

    private fun registerPlayers(playerIds: List<String>): com.boardbanker.core.model.GameSession {
        var result = engine.process(
            TestFixtures.emptySession("SCAN_ORDER"),
            GameCommand.CreateGame("SCAN_ORDER"),
        )
        for (playerId in playerIds) {
            result = engine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, scanNames[playerId]!!),
            )
        }
        return result.session
    }

    private fun startGame(playerIds: List<String>): com.boardbanker.core.model.GameSession {
        val session = registerPlayers(playerIds)
        return engine.process(session, GameCommand.StartGame).session
    }
}
