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
class PlayerDetailsViewModelTest {
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
    fun useJailPass_releasesPlayerWithoutChargingFee() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val indiaExecutor = BankingCommandExecutor(manager)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        val balanceBefore = session.players["USR_01"]!!.balance
        indiaExecutor.execute(GameCommand.ApplyEvent("EVT_11", "USR_01"))
        indiaExecutor.execute(GameCommand.SendPlayerToJail("USR_01"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.jailPassCount)
        viewModel.onGetOutOfJail()
        assertEquals("Use Jail Pass", viewModel.jailPassActionLabel())
        viewModel.onUseJailPass()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(0, viewModel.uiState.value.jailPassCount)
        assertEquals(balanceBefore, manager.currentSession()!!.players["USR_01"]!!.balance)
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("No Jail fee was charged"))
    }

    @Test
    fun jailPassActionLabel_showsCountWhenMultiplePasses() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        val jailedWithTwoPasses = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(jailStatus = true, jailPassCount = 2)
            ),
        )
        repository.save(jailedWithTwoPasses)
        manager.restoreFromStorage()

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals("Use Jail Pass (2)", viewModel.jailPassActionLabel())
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
