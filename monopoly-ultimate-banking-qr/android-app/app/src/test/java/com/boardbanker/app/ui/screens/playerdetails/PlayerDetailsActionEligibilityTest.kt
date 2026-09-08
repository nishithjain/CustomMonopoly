package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GoCollectionReason
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
class PlayerDetailsActionEligibilityTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var executor: BankingCommandExecutor
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
        executor = BankingCommandExecutor(sessionManager)
        audio = RecordingGameAudioFeedback()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startTwoPlayerGame(): String {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
        return sessionManager.currentSession()!!.turnState!!.activePlayerId
    }

    private fun createViewModel(playerId: String): PlayerDetailsViewModel =
        PlayerDetailsViewModel(
            playerId = playerId,
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = audio,
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun nonActivePlayerDetails_disableAllGameplayActions() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isActiveTurn)
        val availability = viewModel.uiState.value.actionAvailability
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertFalse(availability.getOutOfJailEnabled)
        assertEquals(
            "Actions are disabled because it is Nishith's turn.",
            availability.actionsDisabledReason,
        )
    }

    @Test
    fun clickingDisabledCollectGoDoesNotAdvanceStep() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        viewModel.onCollectGo()
        advanceUntilIdle()

        assertEquals(PlayerDetailsStep.Hub, viewModel.uiState.value.step)
    }

    @Test
    fun directBankActionForNonActivePlayerIsRejectedByEngine() = runTest {
        startTwoPlayerGame()
        val before = sessionManager.currentSession()!!
        val balanceBefore = before.players["USR_02"]!!.balance
        val txCountBefore = before.transactions.size

        val outcome = executor.execute(
            GameCommand.PayGoSalary("USR_02", GoCollectionReason.MANUAL_BANK_ACTION),
        )
        advanceUntilIdle()

        assertTrue(outcome is com.boardbanker.app.banking.BankingCommitOutcome.Rejected)
        val rejected = outcome as com.boardbanker.app.banking.BankingCommitOutcome.Rejected
        assertEquals(GameOutcome.REJECTED, rejected.result.outcome)
        assertTrue(rejected.result.error is GameError.NotActivePlayer)

        val after = sessionManager.currentSession()!!
        assertEquals(balanceBefore, after.players["USR_02"]!!.balance)
        assertEquals(txCountBefore, after.transactions.size)
        assertEquals("USR_01", after.turnState!!.activePlayerId)
    }

    @Test
    fun activePlayerDetails_enablePermittedActions() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_01")
        advanceUntilIdle()

        val availability = viewModel.uiState.value.actionAvailability
        assertTrue(availability.collectGoEnabled)
        assertTrue(availability.locationEnabled)
        assertTrue(availability.goToJailEnabled)
        assertFalse(availability.getOutOfJailEnabled)
    }

    @Test
    fun turnChangeUpdatesAvailabilityForOpenPlayerDetails() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.actionAvailability.collectGoEnabled)

        executor.execute(GameCommand.EndTurn("USR_01"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isActiveTurn)
        assertTrue(viewModel.uiState.value.actionAvailability.collectGoEnabled)
    }

    @Test
    fun nonActiveJailedPlayerCannotUseGetOutOfJail() = runTest {
        startTwoPlayerGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_02"))
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.inJail)
        assertFalse(viewModel.uiState.value.actionAvailability.getOutOfJailEnabled)

        viewModel.onGetOutOfJail()
        advanceUntilIdle()
        assertEquals(PlayerDetailsStep.Hub, viewModel.uiState.value.step)
    }

    @Test
    fun activeJailedPlayerEnablesOnlyGetOutOfJail() = runTest {
        startTwoPlayerGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel("USR_01")
        advanceUntilIdle()

        val availability = viewModel.uiState.value.actionAvailability
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertTrue(availability.getOutOfJailEnabled)
    }

    @Test
    fun rejectedNonActiveActionDoesNotPlaySuccessSound() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        viewModel.onConfirmGo()
        advanceUntilIdle()

        assertTrue(audio.gameplayCalls.isEmpty())
        assertEquals("USR_01", sessionManager.currentSession()!!.turnState!!.activePlayerId)
    }

    @Test
    fun rejectedActionShowsNamedTurnMessage() = runTest {
        startTwoPlayerGame()
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        viewModel.onConfirmGo()
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.result!!.primaryMessage.contains(
                "This action cannot be applied to Aditya because it is Nishith's turn.",
            ),
        )
    }
}
