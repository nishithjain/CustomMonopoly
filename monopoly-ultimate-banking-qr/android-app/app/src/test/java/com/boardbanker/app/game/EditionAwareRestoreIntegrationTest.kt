package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.TestEditionResources
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.repository.EditionAwareGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import com.boardbanker.core.persistence.SessionRestoreValidator
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionAwareRestoreIntegrationTest {
    @Test
    fun indiaRestoreNeverLoadsUkDefinitions() = runTest {
        val loadedEditionIds = mutableListOf<String>()
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { editionId ->
                loadedEditionIds += editionId
                AppTestSupport.editionRepository.load(editionId)
            },
            manifestLoader = { AppTestSupport.editionRepository.loadManifest(it) },
        )
        val indiaEngine = DefaultGameEngine(AppTestSupport.editionRepository.load(EditionIds.INDIA))
        var result = indiaEngine.process(
            GameSession(gameId = "INDIA_ONLY", editionId = EditionIds.INDIA, editionDefinitionVersion = 1),
            GameCommand.CreateGame("INDIA_ONLY"),
        )
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = indiaEngine.process(result.session, GameCommand.StartGame)
        repository.save(result.session.copy(status = GameStatus.ACTIVE))

        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val load = editionAware.loadLatestActive()

        assertTrue(load is SavedGameLoadResult.Success)
        assertEquals(listOf(EditionIds.INDIA), loadedEditionIds.distinct())
        assertTrue(loadedEditionIds.none { it == EditionIds.UK })
    }

    @Test
    fun customEditionRestoreUsesCustomBoardLayoutAndIds() = runTest {
        val customDefinitions = loadCustomTestEdition()
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { customDefinitions },
            manifestLoader = { customDefinitions.edition!! },
        )
        val customEngine = DefaultGameEngine(customDefinitions)
        var result = customEngine.process(
            GameSession(gameId = "CUSTOM", editionId = "custom-test", editionDefinitionVersion = 1),
            GameCommand.CreateGame("CUSTOM"),
        )
        result = customEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = customEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = customEngine.process(result.session, GameCommand.StartGame)
        result = customEngine.process(result.session, GameCommand.PurchaseProperty("USR_01", "CTP_01"))
        repository.save(result.session.copy(status = GameStatus.ACTIVE))

        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val load = editionAware.loadLatestActive() as SavedGameLoadResult.Success

        assertEquals("custom-test", load.session.editionId)
        assertTrue(load.session.properties.containsKey("CTP_01"))
        assertEquals(32, customDefinitions.boardLayout.size)
        assertTrue(customDefinitions.boardLayout.spaces.any { it.spaceId == "PROPERTY_CTP_01_SPACE" })
        assertTrue(customDefinitions.events.containsKey("CEV_01"))
    }

    @Test
    fun customEditionInvalidPropertyFailsAfterCustomValidation() = runTest {
        val customDefinitions = loadCustomTestEdition()
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { customDefinitions },
            manifestLoader = { customDefinitions.edition!! },
        )
        repository.save(
            GameSession(
                gameId = "CUSTOM_BAD",
                editionId = "custom-test",
                editionDefinitionVersion = 1,
                status = GameStatus.ACTIVE,
                players = mapOf("USR_01" to com.boardbanker.core.model.PlayerState("USR_01", "Nishith", 1500)),
                properties = mapOf("CTP_MISSING" to PropertyState("CTP_MISSING", "USR_01", 1)),
            ),
        )

        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val load = editionAware.loadLatestActive()

        assertTrue(load is SavedGameLoadResult.SessionValidationFailed)
        assertEquals("custom-test", (load as SavedGameLoadResult.SessionValidationFailed).editionId)
    }

    @Test
    fun restoreFailureDoesNotModifyStoredSave() = runTest {
        val repository = FakeGameSessionRepository()
        val original = GameSession(
            gameId = "UNCHANGED",
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
        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        assertTrue(editionAware.loadLatestActive() is SavedGameLoadResult.IncompatibleEditionVersion)
        assertEquals(original, (repository.load(original.gameId) as SavedGameLoadResult.Success).session)
    }

    @Test
    fun activeSessionManagerBindsRestoredEditionBeforeEngineUse() = runTest {
        val repository = FakeGameSessionRepository()
        val editionAware = editionAwareRepository(repository)
        val store = CommittedGameSessionStore(editionAware)
        val manager = ActiveGameSessionManager(
            editionResolver = { AppTestSupport.editionRepository.load(it) },
            committedStore = store,
            repository = editionAware,
        )
        val indiaEngine = DefaultGameEngine(AppTestSupport.editionRepository.load(EditionIds.INDIA))
        var result = indiaEngine.process(
            GameSession(gameId = "BIND_INDIA", editionId = EditionIds.INDIA, editionDefinitionVersion = 1),
            GameCommand.CreateGame("BIND_INDIA"),
        )
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = indiaEngine.process(result.session, GameCommand.StartGame)
        repository.save(result.session.copy(status = GameStatus.ACTIVE))

        val load = manager.restoreFromStorage()
        assertTrue(load is SavedGameLoadResult.Success)
        assertEquals(EditionIds.INDIA, manager.currentDefinitions().editionId)
        assertEquals("Cubbon Park", manager.currentDefinitions().properties["PRP_01"]!!.name)
    }

    @Test
    fun saveValidatesAgainstActiveEditionNotCatalogDefault() = runTest {
        val customDefinitions = loadCustomTestEdition()
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { editionId ->
                if (editionId == "custom-test") customDefinitions else AppTestSupport.editionRepository.load(editionId)
            },
            manifestLoader = { editionId ->
                if (editionId == "custom-test") customDefinitions.edition!! else AppTestSupport.editionRepository.loadManifest(editionId)
            },
        )
        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val customEngine = DefaultGameEngine(customDefinitions)
        var result = customEngine.process(
            GameSession(gameId = "SAVE_CUSTOM", editionId = "custom-test", editionDefinitionVersion = 1),
            GameCommand.CreateGame("SAVE_CUSTOM"),
        )
        result = customEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = customEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = customEngine.process(result.session, GameCommand.StartGame)
        result = customEngine.process(result.session, GameCommand.PurchaseProperty("USR_01", "CTP_01"))

        assertTrue(editionAware.save(result.session) is com.boardbanker.app.persistence.repository.SaveSessionResult.Success)
        assertTrue(
            SessionRestoreValidator(AppTestSupport.definitions).validate(result.session).any { it.contains("CTP_01") },
        )
    }

    private fun editionAwareRepository(repository: FakeGameSessionRepository): EditionAwareGameSessionRepository =
        EditionAwareGameSessionRepository(
            rawReader = repository,
            storage = repository,
            restoreOrchestrator = SavedGameRestoreOrchestrator(
                serializer = KotlinGameSessionSerializer(),
                editionLoader = { AppTestSupport.editionRepository.load(it) },
                manifestLoader = { AppTestSupport.editionRepository.loadManifest(it) },
            ),
        )

    private fun loadCustomTestEdition() = TestEditionResources.loadCustomTestEdition()
}
