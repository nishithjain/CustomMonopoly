package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetOutOfJailWorkflowTest {
    private val testDispatcher = StandardTestDispatcher()
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun createIndiaJailedViewModel(
        activePlayerId: String = "USR_02",
    ): Pair<PlayerDetailsViewModel, ActiveGameSessionManager> {
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                manager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        if (activePlayerId == "USR_02") {
            session = (
                manager.processCommand(session, GameCommand.EndTurn("USR_01")) as ProcessCommitResult.Committed
                ).session
        }
        session = (
            manager.processCommand(session, GameCommand.SendPlayerToJail(activePlayerId))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = PlayerDetailsViewModel(
            playerId = activePlayerId,
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        return viewModel to manager
    }

    @Test
    fun cancelReturnsToHubWithoutChangingJailStatus() = runTest {
        val (viewModel, _) = createIndiaJailedViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        assertEquals(PlayerDetailsStep.GetOutOfJailChoice, viewModel.uiState.value.step)
        viewModel.onBack()
        advanceUntilIdle()

        assertEquals(PlayerDetailsStep.Hub, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.inJail)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun payJailFeeUsesEditionFeeAndReleasesPlayer() = runTest {
        val (viewModel, sessionManager) = createIndiaJailedViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(
            formatMoney(
                indiaDefinitions.bankingValues.startingBalance - indiaDefinitions.bankingValues.jailReleaseFee,
                indiaDefinitions,
            ),
            viewModel.uiState.value.balanceText,
        )
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("paid"))
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("Released from Jail"))

        val entries = TransactionHistoryEntries.build(sessionManager.currentSession()!!, indiaDefinitions)
        assertTrue(entries.any { it.subtitle == "Get out of Jail fee" })
        assertTrue(
            entries.any {
                it.detail is com.boardbanker.app.ui.screens.history.HistoryDetail.PlayerTransfer &&
                    (it.detail as com.boardbanker.app.ui.screens.history.HistoryDetail.PlayerTransfer).amount ==
                    formatMoney(indiaDefinitions.bankingValues.jailReleaseFee, indiaDefinitions)
            },
        )
    }

    @Test
    fun releaseAfterDoublesReleasesWithoutChargingFee() = runTest {
        val (viewModel, sessionManager) = createIndiaJailedViewModel()
        advanceUntilIdle()
        val balanceBefore = viewModel.uiState.value.balanceText

        viewModel.onGetOutOfJail()
        viewModel.onReleaseAfterDoubles()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(balanceBefore, viewModel.uiState.value.balanceText)
        assertEquals(PlayerDetailsStep.Hub, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.result!!.title.contains("RELEASED FROM JAIL"))

        val session = sessionManager.currentSession()!!
        assertFalse(session.players["USR_02"]!!.jailStatus)
        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)
        assertTrue(entries.any { it.title == "Jail" })
        assertFalse(session.transactions.any { it.transactionType == TransactionType.BANK_DEBIT && it.playerId == "USR_02" })
    }

    @Test
    fun jailPassScanStillReleasesWithoutFee() = runTest {
        val (viewModel, _) = createIndiaJailedViewModel()
        advanceUntilIdle()
        val balanceBefore = viewModel.uiState.value.balanceText

        viewModel.onGetOutOfJail()
        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("EVT_11", CardType.EVENT)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(balanceBefore, viewModel.uiState.value.balanceText)
    }

    @Test
    fun invalidJailPassScanLeavesPlayerInJail() = runTest {
        val (viewModel, _) = createIndiaJailedViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("EVT_01", CardType.EVENT)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.inJail)
        assertEquals(PlayerDetailsStep.GetOutOfJailChoice, viewModel.uiState.value.step)
    }

    @Test
    fun nonActiveJailedPlayerCannotOpenGetOutOfJailFlow() = runTest {
        val repository = FakeGameSessionRepository()
        val sessionManager = AppTestSupport.sessionManager(repository)
        var session = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        session = (sessionManager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.EndTurn("USR_01")) as ProcessCommitResult.Committed).session
        session = (
            sessionManager.processCommand(session, GameCommand.SendPlayerToJail("USR_01"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = sessionManager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        advanceUntilIdle()

        assertEquals(PlayerDetailsStep.Hub, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.inJail)
    }

    @Test
    fun repeatedPayJailFeeOnlyAppliesOnce() = runTest {
        val (viewModel, _) = createIndiaJailedViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(
            formatMoney(
                indiaDefinitions.bankingValues.startingBalance - indiaDefinitions.bankingValues.jailReleaseFee,
                indiaDefinitions,
            ),
            viewModel.uiState.value.balanceText,
        )
    }

    @Test
    fun getOutOfJailChoiceKeepsReleaseActionsEnabled() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = true,
            activePlayerName = "Player B",
            inJail = true,
            commandInFlight = false,
            step = PlayerDetailsStep.GetOutOfJailChoice,
        )
        assertTrue(availability.getOutOfJailEnabled)
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
    }
}
