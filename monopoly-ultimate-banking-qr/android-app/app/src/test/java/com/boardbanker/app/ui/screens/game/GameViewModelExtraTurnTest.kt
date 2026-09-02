package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelExtraTurnTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
    private lateinit var viewModel: GameViewModel
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startIndiaGame(playerIds: List<String> = listOf("USR_01", "USR_02")) {
        var session = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in playerIds) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private fun createViewModel(): GameViewModel =
        GameViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA),
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    private suspend fun grantExtraTurn(playerId: String) {
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_24", playerId))
    }

  private suspend fun grantSkipTo(playerId: String) {
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_18", playerId))
    }

    @Test
    fun extraTurnLabelAppearsAfterEndTurn() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals(TurnKind.EXTRA, viewModel.uiState.value.turnKind)
        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("Nishith's Extra Turn"))
    }

    @Test
    fun gameplayControlsRemainEnabledDuringExtraTurn() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.gameplayLocked)
        assertFalse(viewModel.uiState.value.commandInFlight)
        assertEquals(TurnKind.EXTRA, viewModel.uiState.value.turnKind)
    }

    @Test
    fun samePlayerRemainsDisplayedDuringExtraTurn() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
        assertEquals("Nishith", viewModel.uiState.value.activePlayerName)
    }

    @Test
    fun skipCancellationMessageAppearsOnce() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")
        grantSkipTo("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        val message = viewModel.uiState.value.result!!.primaryMessage
        assertEquals(1, message.split("skipped turn cancelled the extra turn", ignoreCase = true).size - 1)
        assertTrue(message.contains("Nishith's skipped turn cancelled the extra turn"))
    }

    @Test
    fun jailCancellationMessageAppearsOnce() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")
        val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val engine = DefaultGameEngine(definitions)
        val session = sessionManager.currentSession()!!
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_01"))
        val mapped = GameplayResultMapper(definitions).mapEventResult(result, "EVT_12")

        assertEquals(1, mapped.primaryMessage.split("extra turn was cancelled by Jail", ignoreCase = true).size - 1)
        assertTrue(mapped.primaryMessage.contains("Nishith's extra turn was cancelled by Jail"))
    }

    @Test
    fun recompositionDoesNotRepeatExtraTurnTransition() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(TurnKind.EXTRA, viewModel.uiState.value.turnKind)
        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun restartDuringExtraTurnRestoresIndicator() = runTest {
        startIndiaGame()
        grantExtraTurn("USR_01")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        repository.deleteAll()
        repository.save(session)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(TurnKind.EXTRA, viewModel.uiState.value.turnKind)
        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
    }
}
