package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedBankingActivePlayerTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun activePlayerBCollectGoUsesPlayerBWithoutScan() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startGameWithActivePlayer(sessionManager, activePlayerId = "USR_02")
        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCollectGo()
        advanceUntilIdle()

        val step = viewModel.uiState.value.step
        assertTrue(step is AdvancedBankingStep.GoConfirm)
        assertEquals("USR_02", (step as AdvancedBankingStep.GoConfirm).playerId)
        assertEquals("USR_02", viewModel.uiState.value.hubEligibility.activePlayerId)
    }

    @Test
    fun jailedActivePlayerDisablesCollectGoAndLocation() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startGameWithActivePlayer(sessionManager, activePlayerId = "USR_02", jailed = true)
        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hubEligibility.collectGoEnabled)
        assertFalse(viewModel.uiState.value.hubEligibility.locationEnabled)
        assertFalse(viewModel.uiState.value.hubEligibility.goToJailEnabled)
        assertTrue(viewModel.uiState.value.hubEligibility.getOutOfJailEnabled)
    }

    @Test
    fun collectGoCreditsOnlyActivePlayer() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startGameWithActivePlayer(sessionManager, activePlayerId = "USR_02")
        val beforeB = sessionManager.currentSession()!!.players["USR_02"]!!.balance
        val beforeA = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCollectGo()
        viewModel.onConfirmGo("USR_02")
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        assertEquals(
            beforeB + AppTestSupport.definitions.bankingValues.goSalary,
            session.players["USR_02"]!!.balance,
        )
        assertEquals(beforeA, session.players["USR_01"]!!.balance)
    }

    private fun createViewModel(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
    ): AdvancedBankingViewModel = AdvancedBankingViewModel(
        sessionManager = sessionManager,
        definitions = AppTestSupport.definitions,
        locationWorkflowHolder = LocationWorkflowHolder(),
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    private suspend fun startGameWithActivePlayer(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
        activePlayerId: String,
        jailed: Boolean = false,
    ) {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        session = (sessionManager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        if (activePlayerId == "USR_02") {
            session = (
                sessionManager.processCommand(session, GameCommand.EndTurn("USR_01"))
                    as ProcessCommitResult.Committed
                ).session
        }
        if (jailed) {
            session = (
                sessionManager.processCommand(session, GameCommand.SendPlayerToJail(activePlayerId))
                    as ProcessCommitResult.Committed
                ).session
        }
    }
}
