package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
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
class PlayerDetailsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var executor: BankingCommandExecutor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = FakeGameSessionRepository()
        sessionManager = ActiveGameSessionManager(
            definitions = AppTestSupport.definitions,
            committedStore = CommittedGameSessionStore(repository),
            repository = repository,
            engine = AppTestSupport.engine,
        )
        executor = BankingCommandExecutor(sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startActiveGame() {
        var session = (sessionManager.createNewGame() as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private fun createViewModel(playerId: String = "USR_01"): PlayerDetailsViewModel =
        PlayerDetailsViewModel(
            playerId = playerId,
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun playerDetails_showsCustomNameAndBalance() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Nishith", viewModel.uiState.value.playerName)
        assertEquals("M1500", viewModel.uiState.value.balanceText)
        assertEquals("Car", viewModel.uiState.value.tokenName)
    }

    @Test
    fun collectGo_updatesPlayerDetailsBalanceImmediately() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCollectGo()
        viewModel.onConfirmGo()
        advanceUntilIdle()

        assertEquals("M1700", viewModel.uiState.value.balanceText)
    }

    @Test
    fun sendToJail_updatesJailStatusImmediately() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onGoToJail()
        viewModel.onConfirmGoToJail()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.inJail)
        assertEquals("IN JAIL", viewModel.uiState.value.jailStatusText)
    }

    @Test
    fun payJailFee_clearsJailAndUpdatesBalance() = runTest {
        startActiveGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals("M1400", viewModel.uiState.value.balanceText)
    }

    @Test
    fun ownedProperties_sortedByBoardSequence() = runTest {
        startActiveGame()
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_22"))
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_05"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("PRP_01", "PRP_05", "PRP_22"), viewModel.uiState.value.ownedProperties.map { it.propertyId })
        assertTrue(viewModel.uiState.value.ownedProperties.all { it.currentRentText.startsWith("M") })
    }
}
