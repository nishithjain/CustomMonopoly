package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveGameSessionManagerTest {
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var manager: ActiveGameSessionManager

    @Before
    fun setUp() {
        repository = FakeGameSessionRepository()
        manager = AppTestSupport.sessionManager(repository)
    }

    @Test
    fun createNewGamePersistsSetupSession() = runTest {
        val result = manager.createNewGame(EditionIds.UK)
        assertTrue(result is ProcessCommitResult.Committed)
        val session = (result as ProcessCommitResult.Committed).session
        assertEquals(GameStatus.SETUP, session.status)
        assertEquals(EditionIds.UK, session.editionId)
        assertEquals(1, session.editionDefinitionVersion)
        assertEquals(1, repository.saveCallCount)
    }

    @Test
    fun registerTwoPlayersAndStartGame() = runTest {
        val created = manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed
        var session = created.session

        val first = manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        assertTrue(first is ProcessCommitResult.Committed)
        session = (first as ProcessCommitResult.Committed).session
        assertEquals(1500, session.players["USR_01"]!!.balance)

        val second = manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        assertTrue(second is ProcessCommitResult.Committed)
        session = (second as ProcessCommitResult.Committed).session

        val started = manager.processCommand(session, GameCommand.StartGame)
        assertTrue(started is ProcessCommitResult.Committed)
        val active = (started as ProcessCommitResult.Committed).session
        assertEquals(GameStatus.ACTIVE, active.status)
        assertEquals(1500, active.players["USR_01"]!!.balance)
        assertEquals(1500, active.players["USR_02"]!!.balance)
        assertEquals(4, repository.saveCallCount)
    }

    @Test
    fun registerFourPlayersBlocksFifth() = runTest {
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02", "USR_03", "USR_04")) {
            val result = manager.processCommand(
                session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            )
            assertTrue(result is ProcessCommitResult.Committed)
            session = (result as ProcessCommitResult.Committed).session
        }
        val rejected = manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        assertTrue(rejected is ProcessCommitResult.Rejected)
        assertTrue((rejected as ProcessCommitResult.Rejected).result.error is GameError.DuplicatePlayer)
    }

    @Test
    fun purchasePropertyUpdatesBalanceAndOwnership() = runTest {
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session

        val purchase = manager.processCommand(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        assertTrue(purchase is ProcessCommitResult.Committed)
        val updated = (purchase as ProcessCommitResult.Committed).session
        assertEquals("USR_01", updated.properties["PRP_01"]!!.ownerPlayerId)
        assertEquals(1500 - 60, updated.players["USR_01"]!!.balance)
    }

    @Test
    fun duplicatePurchaseRejectedWithoutExtraSave() = runTest {
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.PurchaseProperty("USR_01", "PRP_07")) as ProcessCommitResult.Committed).session
        val owned = session
        val savesBefore = repository.saveCallCount
        val rejected = manager.processCommand(
            owned,
            GameCommand.PurchaseProperty("USR_01", "PRP_07"),
        )
        assertTrue(rejected is ProcessCommitResult.Rejected)
        assertEquals(savesBefore, repository.saveCallCount)
    }

    @Test
    fun setupSessionSurvivesRestore() = runTest {
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session

        val restoredManager = AppTestSupport.sessionManager(repository)
        val load = restoredManager.restoreFromStorage()
        assertTrue(load is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (load as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(2, restored.players.size)
        assertEquals(1500, restored.players["USR_01"]!!.balance)
        assertEquals(1500, restored.players["USR_02"]!!.balance)
        assertEquals(GameStatus.SETUP, restored.status)
    }

    @Test
    fun activeSessionSurvivesRestore() = runTest {
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        val gameId = session.gameId
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session

        val restoredManager = AppTestSupport.sessionManager(repository)
        val load = restoredManager.restoreFromStorage()
        assertTrue(load is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (load as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(gameId, restored.gameId)
        assertEquals(GameStatus.ACTIVE, restored.status)
        assertEquals(2, restored.players.size)
    }

    @Test
    fun deleteCurrentGameClearsSavedSession() = runTest {
        manager.createNewGame(EditionIds.UK)
        assertTrue(manager.hasResumableGame())
        manager.deleteCurrentGame()
        assertFalse(manager.hasResumableGame())
    }

    @Test
    fun createNewGameWithIndiaPersistsIndiaEditionId() = runTest {
        val indiaManager = AppTestSupport.sessionManager(repository)
        val result = indiaManager.createNewGame(EditionIds.INDIA)
        assertTrue(result is ProcessCommitResult.Committed)
        val session = (result as ProcessCommitResult.Committed).session
        assertEquals(EditionIds.INDIA, session.editionId)
        assertEquals(EditionIds.INDIA, indiaManager.currentDefinitions().editionId)
    }

    @Test
    fun restoreIndiaGameBindsIndiaDefinitions() = runTest {
        val indiaManager = AppTestSupport.sessionManager(repository)
        var session = (indiaManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        session = (
            indiaManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
                as ProcessCommitResult.Committed
            ).session
        session = (
            indiaManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
                as ProcessCommitResult.Committed
            ).session
        session = (indiaManager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session

        val restoredManager = AppTestSupport.sessionManager(repository)
        val load = restoredManager.restoreFromStorage()
        assertTrue(load is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (load as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(EditionIds.INDIA, restored.editionId)
        assertEquals(EditionIds.INDIA, restoredManager.currentDefinitions().editionId)
        assertEquals("Cubbon Park", restoredManager.currentDefinitions().properties["PRP_01"]!!.name)
    }
}
