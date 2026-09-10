package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.navigation.ActiveGameHubReturnSignal
import com.boardbanker.app.gameplay.presentation.DiceGambleStatus
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameSessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun configureSessionManager(vararg rolls: Pair<Int, Int>) {
        sessionManager = AppTestSupport.sessionManager(
            repository = repository,
            diceRoller = SequenceDiceRoller(*rolls),
        )
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
            activeGameHubReturnSignal = ActiveGameHubReturnSignal(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun initialButtonSaysRollDiceAndIsEnabled() = runTest {
        configureSessionManager(3 to 5)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val gamble = viewModel.uiState.value.diceGamble!!
        assertEquals("Roll Dice", gamble.rollButtonLabel)
        assertTrue(gamble.rollEnabled)
        assertEquals("Attempt 1 of 3", gamble.attemptLabel)
        assertEquals(DiceGambleStatus.WAITING_TO_ROLL, gamble.status)
        assertFalse(viewModel.uiState.value.luckyBreakRollInProgress)
    }

    @Test
    fun pendingGambleShowsLuckyBreakPanel() = runTest {
        configureSessionManager(3 to 3)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble)
        assertNotNull(viewModel.uiState.value.diceGamble)
        assertEquals("Lucky Break", viewModel.uiState.value.diceGamble!!.eventName)
    }

    @Test
    fun doublesResultStaysVisibleUntilContinue() = runTest {
        configureSessionManager(4 to 4)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.pendingDiceGamble)
        assertNull(viewModel.uiState.value.result)
        assertNotNull(viewModel.uiState.value.luckyBreakCompletedOutcome)
        val gamble = viewModel.uiState.value.diceGamble!!
        assertTrue(gamble.showContinue)
        assertEquals("Doubles!", gamble.outcomeHeadline)
        assertEquals(4, gamble.dieOne)
        assertEquals(4, gamble.dieTwo)
        assertFalse(viewModel.uiState.value.luckyBreakRollInProgress)

        viewModel.onLuckyBreakContinue()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.diceGamble)
        assertNull(viewModel.uiState.value.luckyBreakCompletedOutcome)
    }

    @Test
    fun doubleTapDispatchesOnce() = runTest {
        configureSessionManager(1 to 2, 3 to 3)
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
    fun failedRollShowsDiceAndRollAgain() = runTest {
        configureSessionManager(1 to 2)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        val gamble = viewModel.uiState.value.diceGamble!!
        assertEquals(1, gamble.dieOne)
        assertEquals(2, gamble.dieTwo)
        assertEquals("No doubles — 2 attempts remaining", gamble.attemptLabel)
        assertEquals("Roll Again", gamble.rollButtonLabel)
        assertTrue(gamble.rollEnabled)
        assertEquals(DiceGambleStatus.WAITING_TO_ROLL, gamble.status)
        assertFalse(viewModel.uiState.value.luckyBreakRollInProgress)
    }

    @Test
    fun rollInProgressResetsAfterSuccessAndFailure() = runTest {
        configureSessionManager(1 to 2, 4 to 4)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.luckyBreakRollInProgress)

        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.luckyBreakRollInProgress)
    }

    @Test
    fun thirdFailedAttemptShowsPenaltyUntilContinue() = runTest {
        configureSessionManager(1 to 2, 2 to 3, 4 to 5)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        val gamble = viewModel.uiState.value.diceGamble!!
        assertEquals(4, gamble.dieOne)
        assertEquals(5, gamble.dieTwo)
        assertEquals("No doubles", gamble.outcomeHeadline)
        assertTrue(gamble.showContinue)
        assertEquals(145_000, sessionManager.currentSession()!!.players["USR_01"]!!.balance)
    }

    @Test
    fun jackpotOrPenaltyAppliedOnlyOnce() = runTest {
        configureSessionManager(4 to 4)
        startIndiaGame()
        applyLuckyBreak()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        val credits = sessionManager.currentSession()!!
            .transactions
            .count { it.transactionType == TransactionType.BANK_CREDIT }
        assertEquals(1, credits)

        viewModel.onRollLuckyBreakDice()
        advanceUntilIdle()

        val creditsAfterSecondTap = sessionManager.currentSession()!!
            .transactions
            .count { it.transactionType == TransactionType.BANK_CREDIT }
        assertEquals(1, creditsAfterSecondTap)
    }

    @Test
    fun unrelatedScanBlockedDuringGamble() = runTest {
        configureSessionManager(1 to 1)
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
        configureSessionManager(1 to 3)
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
