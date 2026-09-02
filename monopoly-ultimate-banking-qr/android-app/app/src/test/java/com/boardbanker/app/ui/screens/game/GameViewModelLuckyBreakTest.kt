package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.gameplay.presentation.DiceGambleStatus
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
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
class GameViewModelLuckyBreakTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
    private lateinit var diceRoller: SequenceDiceRoller

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

    private suspend fun applyLuckyBreak() {
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_17", "USR_01"))
    }

    private fun createViewModel(): GameViewModel =
        GameViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA),
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
            diceRoller = diceRoller,
        )

    @Test
    fun pendingGambleShowsLuckyBreakPanel() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(3, 3)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble)
        assertNotNull(viewModel.uiState.value.diceGamble)
        assertEquals("Lucky Break", viewModel.uiState.value.diceGamble!!.eventName)
    }

    @Test
    fun rollDispatchesSingleCommandAndShowsSuccess() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(4, 4)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.pendingDiceGamble)
        assertNotNull(viewModel.uiState.value.result)
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("won"))
    }

    @Test
    fun doubleTapDispatchesOnce() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(1, 2), listOf(3, 3)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        assertEquals(1, sessionManager.currentSession()!!.pendingDiceGamble!!.attemptsUsed)
    }

    @Test
    fun buttonReenabledAfterNonFinalFailure() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(1, 2)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.diceGamble!!.rollEnabled)
        assertEquals(DiceGambleStatus.WAITING_TO_ROLL, viewModel.uiState.value.diceGamble!!.status)
    }

    @Test
    fun unrelatedScanBlockedDuringGamble() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(1, 1)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("PRP_01", com.boardbanker.core.card.CardType.PROPERTY)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble)
    }

    @Test
    fun restoreAfterFailedRollKeepsDiceValues() = runTest {
        diceRoller = SequenceDiceRoller(listOf(listOf(1, 3)).iterator())
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        repository.deleteAll()
        repository.save(sessionManager.currentSession()!!)

        val restored = createViewModel()
        advanceUntilIdle()

        assertEquals(1, restored.uiState.value.diceGamble!!.dieOne)
        assertEquals(3, restored.uiState.value.diceGamble!!.dieTwo)
        assertEquals("No doubles — 2 attempts remaining", restored.uiState.value.diceGamble!!.attemptLabel)
    }
}
