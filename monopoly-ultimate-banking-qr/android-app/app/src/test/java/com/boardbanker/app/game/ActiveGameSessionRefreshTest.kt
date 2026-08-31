package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.ui.screens.game.GameViewModel
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveGameSessionRefreshTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var executor: BankingCommandExecutor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
        executor = BankingCommandExecutor(sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startActiveGame() {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    @Test
    fun committedSessionFlow_emitsAfterGoSalary() = runTest {
        startActiveGame()
        val balances = mutableListOf<Int?>()
        val job = launch(testDispatcher) {
            sessionManager.committedSession.collect { session ->
                balances += session?.players?.get("USR_01")?.balance
            }
        }
        advanceUntilIdle()

        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        executor.execute(GameCommand.PayGoSalary("USR_01"))
        advanceUntilIdle()

        assertEquals(1500, before)
        assertEquals(1700, sessionManager.currentSession()!!.players["USR_01"]!!.balance)
        assertEquals(1700, balances.last())
        job.cancel()
    }

    @Test
    fun gameViewModel_refreshesBalanceAfterExternalBankingCommit() = runTest {
        startActiveGame()
        val viewModel = GameViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals("M1500", viewModel.uiState.value.players.first { it.playerId == "USR_01" }.balanceText)

        executor.execute(GameCommand.PayGoSalary("USR_01"))
        advanceUntilIdle()

        assertEquals("M1700", viewModel.uiState.value.players.first { it.playerId == "USR_01" }.balanceText)
    }

    @Test
    fun gameViewModel_refreshesBalanceAfterLocationFee() = runTest {
        startActiveGame()
        val viewModel = GameViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        executor.execute(GameCommand.PayLocationFee("USR_01", "PRP_01"))
        advanceUntilIdle()

        assertEquals("M1400", viewModel.uiState.value.players.first { it.playerId == "USR_01" }.balanceText)
    }

    @Test
    fun goUndo_restoresGameViewModelBalance() = runTest {
        startActiveGame()
        val viewModel = GameViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        executor.execute(GameCommand.PayGoSalary("USR_01"))
        advanceUntilIdle()
        assertEquals("M1700", viewModel.uiState.value.players.first { it.playerId == "USR_01" }.balanceText)

        executor.execute(GameCommand.UndoLastAction)
        advanceUntilIdle()
        assertEquals("M1500", viewModel.uiState.value.players.first { it.playerId == "USR_01" }.balanceText)
    }
}
