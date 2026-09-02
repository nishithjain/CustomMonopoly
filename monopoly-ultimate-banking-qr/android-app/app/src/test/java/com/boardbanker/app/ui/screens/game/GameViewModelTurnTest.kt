package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTurnTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
    private lateinit var viewModel: GameViewModel

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

    private suspend fun startIndiaGame(playerIds: List<String> = listOf("USR_01", "USR_02", "USR_03")) {
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

  private suspend fun grantSkipTo(playerId: String) {
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_18", playerId))
    }

    @Test
    fun endTurn_displaysFinalActivePlayerAndSkipMessages() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        grantSkipTo("USR_02")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals("USR_01", sessionManager.currentSession()!!.turnState!!.activePlayerId)
        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
        val result = viewModel.uiState.value.result
        assertNotNull(result)
        assertTrue(result!!.primaryMessage.contains("Aditya skips this turn"))
        assertTrue(result.primaryMessage.contains("Nishith's turn."))
    }

    @Test
    fun endTurn_doesNotExposeSkippedPlayerAsActive() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        grantSkipTo("USR_02")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.activePlayerId == "USR_02")
        assertEquals("USR_01", viewModel.uiState.value.activePlayerId)
    }

    @Test
    fun repeatedEndTurn_doesNotConsumeAnotherSkip() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        grantSkipTo("USR_02")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals(0, sessionManager.currentSession()!!.players["USR_02"]!!.pendingSkipTurnCount)
        assertEquals("USR_02", sessionManager.currentSession()!!.turnState!!.activePlayerId)
        assertEquals("USR_02", viewModel.uiState.value.activePlayerId)
    }

    @Test
    fun skipActivityDisplayedOncePerSkippedPlayer() = runTest {
        startIndiaGame()
        grantSkipTo("USR_02")
        grantSkipTo("USR_03")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEndTurn()
        advanceUntilIdle()

        val message = viewModel.uiState.value.result!!.primaryMessage
        assertEquals(2, message.split("skips this turn").size - 1)
        assertTrue(message.contains("Aditya skips this turn"))
        assertTrue(message.contains("Rahul skips this turn"))
    }
}
