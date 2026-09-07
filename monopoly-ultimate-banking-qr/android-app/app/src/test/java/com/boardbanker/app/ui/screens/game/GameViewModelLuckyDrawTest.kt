package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.presentation.EventDrawUiMapper
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.scanner.ScanContext
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(EventDrawUiMapper.REQUIRED_DRAWS_TEXT, viewModel.uiState.value.eventDraw!!.requiredDrawsText)
        assertTrue(viewModel.uiState.value.eventDraw!!.scanEnabled)
        assertFalse(viewModel.uiState.value.commandInFlight)
    }

    @Test
    fun luckyDrawScanButtonEnabledAfterEventWorkflow() = runTest {
        startIndiaGame()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("EVT_15", CardType.EVENT)
        advanceUntilIdle()
        viewModel.onEventContinue()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDrawScanRequired)
        assertNotNull(viewModel.uiState.value.eventDraw)
        assertTrue(viewModel.uiState.value.eventDraw!!.scanEnabled)
        assertFalse(viewModel.uiState.value.commandInFlight)
    }

    @Test
    fun scanButtonDisabledWhileScannerLaunching() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onScanLuckyDrawEventRequested()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.luckyDrawScannerLaunchInProgress)
        assertFalse(viewModel.uiState.value.eventDraw!!.scanEnabled)
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
    fun luckyBreakScanClearsPendingDrawAndOpensDiceWorkflow() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("EVT_17", CardType.EVENT)
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.pendingEventDraw)
        assertNotNull(sessionManager.currentSession()!!.pendingDiceGamble)
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble)
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
    fun scanRequestUsesPendingDrawPurpose() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onScanLuckyDrawEventRequested()
        advanceUntilIdle()

        assertEquals(ScanContext.RESOLVE_PENDING_EVENT_DRAW, viewModel.uiState.value.scanRequest?.context)
        assertTrue(viewModel.uiState.value.luckyDrawScannerLaunchInProgress)
        assertEquals(EventDrawUiMapper.OPENING_SCANNER_LABEL, viewModel.uiState.value.eventDraw!!.scanButtonLabel)
    }

    @Test
    fun scannerCancelReEnablesScanButton() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onScanLuckyDrawEventRequested()
        advanceUntilIdle()
        viewModel.onLuckyDrawScannerCancelled()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.luckyDrawScannerLaunchInProgress)
        assertTrue(viewModel.uiState.value.eventDraw!!.scanEnabled)
        assertEquals(EventDrawUiMapper.SCAN_BUTTON_LABEL, viewModel.uiState.value.eventDraw!!.scanButtonLabel)
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
        assertTrue(restored.uiState.value.eventDraw!!.scanEnabled)
        assertFalse(restored.uiState.value.luckyDrawScannerLaunchInProgress)
    }

    @Test
    fun endTurnAvailableAfterAdditionalEventResolved() = runTest {
        startIndiaGame()
        applyLuckyDraw()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("EVT_11", CardType.EVENT)
        advanceUntilIdle()
        viewModel.onDone()
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.pendingEventDraw)
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertTrue(viewModel.uiState.value.actionAvailability.endTurnEnabled)
    }

    @Test
    fun recreatedViewModelKeepsScanButtonEnabled() = runTest {
        startIndiaGame()
        applyLuckyDraw()
        repository.save(sessionManager.currentSession()!!)

        val first = createViewModel()
        advanceUntilIdle()
        assertTrue(first.uiState.value.eventDraw!!.scanEnabled)

        val recreated = createViewModel()
        advanceUntilIdle()
        assertTrue(recreated.uiState.value.eventDraw!!.scanEnabled)
    }
}
