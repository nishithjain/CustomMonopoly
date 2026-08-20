package com.boardbanker.app

import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.GameStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveGameSessionManagerTest {
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var store: CommittedGameSessionStore
    private lateinit var manager: ActiveGameSessionManager

    @Before
    fun setUp() {
        repository = FakeGameSessionRepository()
        store = CommittedGameSessionStore(repository)
        manager = ActiveGameSessionManager(
            definitions = AppTestSupport.definitions,
            committedStore = store,
            repository = repository,
            engine = AppTestSupport.engine,
        )
    }

    @Test
    fun createNewGamePersistsSetupSession() = runTest {
        val result = manager.createNewGame()
        assertTrue(result is ProcessCommitResult.Committed)
        val session = (result as ProcessCommitResult.Committed).session
        assertEquals(GameStatus.SETUP, session.status)
        assertEquals(1, repository.saveCallCount)
    }

    @Test
    fun registerTwoPlayersAndStartGame() = runTest {
        val created = manager.createNewGame() as ProcessCommitResult.Committed
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
        var session = (manager.createNewGame() as ProcessCommitResult.Committed).session
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
    fun duplicatePlayerRejectedWithoutSave() = runTest {
        var session = (manager.createNewGame() as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        val savesBeforeDuplicate = repository.saveCallCount

        val duplicate = manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        assertTrue(duplicate is ProcessCommitResult.Rejected)
        assertEquals(savesBeforeDuplicate, repository.saveCallCount)
        assertEquals(1, session.players.size)
    }

    @Test
    fun startGameRejectedWithOnePlayer() = runTest {
        var session = (manager.createNewGame() as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        val savesBeforeStart = repository.saveCallCount

        val rejected = manager.processCommand(session, GameCommand.StartGame)
        assertTrue(rejected is ProcessCommitResult.Rejected)
        assertEquals(savesBeforeStart, repository.saveCallCount)
        assertEquals(GameStatus.SETUP, session.status)
    }

    @Test
    fun rejectedCommandDoesNotPersist() = runTest {
        val session = AppTestSupport.newGame()
        val owned = session.copy(
            properties = session.properties + (
                "PRP_07" to com.boardbanker.core.model.PropertyState("PRP_07", "USR_02", 1)
                ),
        )
        store.commitGameResult(com.boardbanker.core.engine.GameResult(session = owned))
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
        var session = (manager.createNewGame() as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session

        val restoredManager = ActiveGameSessionManager(
            definitions = AppTestSupport.definitions,
            committedStore = CommittedGameSessionStore(repository),
            repository = repository,
            engine = AppTestSupport.engine,
        )
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
        var session = (manager.createNewGame() as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        val gameId = session.gameId
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session

        val restoredManager = ActiveGameSessionManager(
            definitions = AppTestSupport.definitions,
            committedStore = CommittedGameSessionStore(repository),
            repository = repository,
            engine = AppTestSupport.engine,
        )
        val load = restoredManager.restoreFromStorage()
        assertTrue(load is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (load as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(gameId, restored.gameId)
        assertEquals(GameStatus.ACTIVE, restored.status)
        assertEquals(2, restored.players.size)
    }

    @Test
    fun deleteCurrentGameClearsSavedSession() = runTest {
        manager.createNewGame()
        assertTrue(manager.hasResumableGame())
        manager.deleteCurrentGame()
        assertFalse(manager.hasResumableGame())
    }
}
