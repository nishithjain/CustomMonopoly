package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.repository.EditionAwareGameSessionRepository
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionDefinitionVersionCompatibilityTest {
    @Test
    fun newUkGameStoresEditionDefinitionVersionOne() = runTest {
        val repository = FakeGameSessionRepository()
        val manager = managerWithEditionAwareRepository(repository)
        val result = manager.createNewGame(EditionIds.UK)
        val session = (result as ProcessCommitResult.Committed).session
        assertEquals(EditionIds.UK, session.editionId)
        assertEquals(1, session.editionDefinitionVersion)
    }

    @Test
    fun newIndiaGameStoresEditionDefinitionVersionTwo() = runTest {
        val repository = FakeGameSessionRepository()
        val manager = managerWithEditionAwareRepository(repository)
        val result = manager.createNewGame(EditionIds.INDIA)
        val session = (result as ProcessCommitResult.Committed).session
        assertEquals(EditionIds.INDIA, session.editionId)
        assertEquals(2, session.editionDefinitionVersion)
    }

    @Test
    fun matchingVersionResumesSuccessfully() = runTest {
        val repository = FakeGameSessionRepository()
        val store = CommittedGameSessionStore(editionAwareRepository(repository))
        val manager = managerWithEditionAwareRepository(repository, store)
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (
            manager.processCommand(session, com.boardbanker.core.command.GameCommand.RegisterPlayer("USR_01", "Nishith"))
                as ProcessCommitResult.Committed
            ).session
        session = (
            manager.processCommand(session, com.boardbanker.core.command.GameCommand.RegisterPlayer("USR_02", "Aditya"))
                as ProcessCommitResult.Committed
            ).session
        session = (manager.processCommand(session, com.boardbanker.core.command.GameCommand.StartGame) as ProcessCommitResult.Committed).session
        repository.save(session)

        val restoredManager = managerWithEditionAwareRepository(
            repository,
            CommittedGameSessionStore(editionAwareRepository(repository)),
        )
        val load = restoredManager.restoreFromStorage()
        assertTrue(load is SavedGameLoadResult.Success)
        assertEquals(1, (load as SavedGameLoadResult.Success).session.editionDefinitionVersion)
    }

    @Test
    fun mismatchedVersionIsRejectedAndSaveRemainsUnchanged() = runTest {
        val repository = FakeGameSessionRepository()
        val original = GameSession(
            gameId = "MISMATCH_GAME",
            editionId = EditionIds.INDIA,
            editionDefinitionVersion = 1,
            status = GameStatus.ACTIVE,
        )
        repository.save(original)

        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { AppTestSupport.editionRepository.load(it) },
            manifestLoader = {
                AppTestSupport.editionRepository.loadManifest(it).copy(definitionVersion = 2)
            },
        )
        val checkingRepository = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val load = checkingRepository.loadLatestActive()

        assertTrue(load is SavedGameLoadResult.IncompatibleEditionVersion)
        val mismatch = load as SavedGameLoadResult.IncompatibleEditionVersion
        assertEquals(EditionIds.INDIA, mismatch.editionId)
        assertEquals(1, mismatch.savedVersion)
        assertEquals(2, mismatch.installedVersion)

        val stored = repository.load(original.gameId) as SavedGameLoadResult.Success
        assertEquals(original, stored.session)
    }

    @Test
    fun mismatchedVersionDoesNotActivateSessionManager() = runTest {
        val repository = FakeGameSessionRepository()
        repository.save(
            GameSession(
                gameId = "NO_ACTIVATE",
                editionId = EditionIds.UK,
                editionDefinitionVersion = 1,
                status = GameStatus.ACTIVE,
            ),
        )
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { AppTestSupport.editionRepository.load(it) },
            manifestLoader = {
                AppTestSupport.editionRepository.loadManifest(it).copy(definitionVersion = 2)
            },
        )
        val checkingRepository = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val store = CommittedGameSessionStore(checkingRepository)
        val manager = ActiveGameSessionManager(
            editionResolver = { AppTestSupport.editionRepository.load(it) },
            committedStore = store,
            repository = checkingRepository,
        )

        val load = manager.restoreFromStorage()
        assertTrue(load is SavedGameLoadResult.IncompatibleEditionVersion)
        assertEquals(null, manager.currentSession())
        assertFalse(manager.hasResumableGame())
    }

    private fun editionAwareRepository(
        rawReader: FakeGameSessionRepository,
    ): EditionAwareGameSessionRepository =
        EditionAwareGameSessionRepository(
            rawReader = rawReader,
            storage = rawReader,
            restoreOrchestrator = SavedGameRestoreOrchestrator(
                serializer = KotlinGameSessionSerializer(),
                editionLoader = { editionId -> AppTestSupport.editionRepository.load(editionId) },
                manifestLoader = { editionId -> AppTestSupport.editionRepository.loadManifest(editionId) },
            ),
        )

    private fun managerWithEditionAwareRepository(
        repository: FakeGameSessionRepository,
        store: CommittedGameSessionStore = CommittedGameSessionStore(editionAwareRepository(repository)),
    ): ActiveGameSessionManager =
        ActiveGameSessionManager(
            editionResolver = { editionId -> AppTestSupport.editionRepository.load(editionId) },
            committedStore = store,
            repository = editionAwareRepository(repository),
        )
}
