package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.card.CardType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelLuckyDrawTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager

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

    private suspend fun startIndiaGame() {
        var session = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private suspend fun applyLuckyDraw() {
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_15", "USR_01"))
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

    @Test
    fun pendingDrawShowsLuckyDrawPanel() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDrawScanRequired)
        assertNotNull(viewModel.uiState.value.eventDraw)
    }

    @Test
    fun validEventScanConsumesPendingDraw() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("EVT_11", CardType.EVENT)
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.pendingEventDraw)
        assertNotNull(viewModel.uiState.value.result)
    }

    @Test
    fun invalidPropertyScanKeepsPanelActive() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertNotNull(sessionManager.currentSession()!!.pendingEventDraw)
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDrawScanRequired)
    }

    @Test
    fun restoreReopensLuckyDrawWorkflow() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.save(sessionManager.currentSession()!!)

        val restored = createViewModel()
        advanceUntilIdle()

        assertTrue(restored.uiState.value.workflowState is GameplayWorkflowState.EventDrawScanRequired)
        assertNotNull(restored.uiState.value.eventDraw)
    }
}
