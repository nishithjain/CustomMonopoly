package com.boardbanker.app.ui.screens.setup

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.GameSessionSchema
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameMetadataReader
import com.boardbanker.core.persistence.SavedGameMetadataReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class GameSetupViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ukIsInitiallySelectedFromCatalogueDefault() = runTest {
        val viewModel = newGameViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.catalogueLoaded)
        assertEquals(EditionIds.UK, state.selectedEditionId)
        assertEquals("UK Edition", state.selectedEditionName)
        assertEquals(listOf("uk", "india"), state.availableEditions.map { it.editionId })
    }

    @Test
    fun selectingIndiaUpdatesNewGameState() = runTest {
        val viewModel = newGameViewModel()
        advanceUntilIdle()
        viewModel.onEditionSelected(EditionIds.INDIA)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(EditionIds.INDIA, state.selectedEditionId)
        assertEquals("India Edition", state.selectedEditionName)
        assertEquals(EditionIds.INDIA, sessionManager.currentSession()?.editionId)
    }

    @Test
    fun startingGamePassesIndiaThroughCreationFlow() = runTest {
        val viewModel = newGameViewModel()
        advanceUntilIdle()
        viewModel.onEditionSelected(EditionIds.INDIA)
        advanceUntilIdle()
        registerMinimumPlayers(viewModel)
        viewModel.startGame()
        advanceUntilIdle()
        val session = sessionManager.currentSession()
        assertNotNull(session)
        assertEquals(EditionIds.INDIA, session!!.editionId)
        assertEquals(GameStatus.ACTIVE, session.status)
        assertEquals(150000, session.players["USR_01"]!!.balance)
    }

    @Test
    fun startGameDisabledWhenCatalogueLoadingFails() = runTest {
        val tempRoot = Files.createTempDirectory("missing-catalogue").resolve("data")
        Files.createDirectories(tempRoot.resolve("common"))
        Files.createDirectories(tempRoot.resolve("editions/uk"))
        val brokenRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val viewModel = GameSetupViewModel(
            sessionManager = sessionManager,
            activeDefinitions = AppTestSupport.definitions,
            createNewGame = true,
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
            editionRepository = brokenRepository,
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.catalogueLoaded)
        assertNotNull(state.catalogueError)
        assertFalse(state.canStartGame)
    }

    @Test
    fun editionSelectionLocksAfterFirstPlayerRegistered() = runTest {
        val viewModel = newGameViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.editionSelectionLocked)
        viewModel.onPlayerIdScanned("USR_01")
        advanceUntilIdle()
        viewModel.confirmPendingRegistration("Nishith")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editionSelectionLocked)
        viewModel.onEditionSelected(EditionIds.INDIA)
        advanceUntilIdle()
        assertEquals(EditionIds.UK, viewModel.uiState.value.selectedEditionId)
    }

    @Test
    fun legacySavedGameWithoutEditionIdIsRecognizedViaSchemaVersion() {
        val serializer = KotlinGameSessionSerializer()
        val original = AppTestSupport.newGame()
        val json = serializer.serialize(original)
            .replace("\"editionId\":\"uk\",", "")
            .replace("\"editionDefinitionVersion\":1,", "")
        val result = SavedGameMetadataReader().read(json, GameSessionSchema.LEGACY_PRE_EDITION_VERSION)
        assertTrue(result is SavedGameMetadataReadResult.Success)
        val metadata = (result as SavedGameMetadataReadResult.Success).metadata
        assertEquals(EditionIds.LEGACY_EDITION_ID, metadata.editionId)
        assertEquals(EditionIds.LEGACY_DEFINITION_VERSION, metadata.editionDefinitionVersion)
    }

    private fun newGameViewModel(): GameSetupViewModel =
        GameSetupViewModel(
            sessionManager = sessionManager,
            activeDefinitions = AppTestSupport.definitions,
            createNewGame = true,
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
            editionRepository = AppTestSupport.editionRepository,
        )

    private suspend fun TestScope.registerMinimumPlayers(viewModel: GameSetupViewModel) {
        viewModel.onPlayerIdScanned("USR_01")
        advanceUntilIdle()
        viewModel.confirmPendingRegistration("Nishith")
        advanceUntilIdle()
        viewModel.onPlayerIdScanned("USR_02")
        advanceUntilIdle()
        viewModel.confirmPendingRegistration("Aditya")
        advanceUntilIdle()
    }
}
