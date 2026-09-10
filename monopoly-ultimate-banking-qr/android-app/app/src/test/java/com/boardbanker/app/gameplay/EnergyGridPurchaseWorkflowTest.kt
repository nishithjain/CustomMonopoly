package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.navigation.ActiveGameHubReturnSignal
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.ui.screens.game.GameViewModel
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameStatus
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
class EnergyGridPurchaseWorkflowTest {
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

    @Test
    fun scanningUnownedEnergyGridShowsBuyDecision() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("ENG_01", CardType.ENERGY_GRID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.UnownedEnergyGridDecision)
        assertNotNull(viewModel.uiState.value.cardPresentation?.buyAmount)
        assertEquals(20000, viewModel.uiState.value.cardPresentation?.buyAmount)
    }

    @Test
    fun buyingEnergyGridDeductsPriceAndAssignsOwnership() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("ENG_01", CardType.ENERGY_GRID)
        advanceUntilIdle()
        viewModel.onBuyProperty()
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        assertEquals("USR_01", session.energyGrids["ENG_01"]?.ownerPlayerId)
        assertTrue(session.transactions.any { it.transactionType == TransactionType.ENERGY_GRID_PURCHASE })
        assertEquals(130000, session.players["USR_01"]!!.balance)
    }

    @Test
    fun ownedEnergyGridAutoResolvesLandingWithoutPlayerScan() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)
        var session = sessionManager.currentSession()!!
        session = (
            sessionManager.processCommand(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("ENG_01", CardType.ENERGY_GRID)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.workflowState is GameplayWorkflowState.WaitingForRentPayerEnergyGrid)
        assertNull(viewModel.uiState.value.scanRequest)
    }

    @Test
    fun concludeGameSetsFinishedStatusWithWinner() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.confirmEndGame()
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        assertEquals(GameStatus.FINISHED, session.status)
        assertNotNull(session.winnerPlayerId)
    }

    @Test
    fun terminationActionsHiddenDuringEnergyGridPurchase() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("ENG_01", CardType.ENERGY_GRID)
        advanceUntilIdle()

        assertFalse(
            com.boardbanker.app.ui.screens.game.ActiveGameCardUiPolicy.showGameTerminationActions(
                workflowState = viewModel.uiState.value.workflowState,
                result = viewModel.uiState.value.result,
                gameplayLocked = viewModel.uiState.value.gameplayLocked,
            ),
        )
    }

    @Test
    fun returningToReadyShowsTerminationActionsAgain() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaGame(sessionManager)

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("ENG_01", CardType.ENERGY_GRID)
        advanceUntilIdle()
        viewModel.onCancelWorkflow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertTrue(
            com.boardbanker.app.ui.screens.game.ActiveGameCardUiPolicy.showGameTerminationActions(
                workflowState = viewModel.uiState.value.workflowState,
                result = viewModel.uiState.value.result,
                gameplayLocked = viewModel.uiState.value.gameplayLocked,
            ),
        )
    }

    private fun createViewModel(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
    ): GameViewModel = GameViewModel(
        sessionManager = sessionManager,
        definitions = indiaDefinitions,
        transientWorkflow = TransientScanWorkflowHolder(),
        locationWorkflowHolder = LocationWorkflowHolder(),
        activeGameHubReturnSignal = ActiveGameHubReturnSignal(),
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    private suspend fun startIndiaGame(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
    ) {
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
        assertNull(session.energyGrids["ENG_01"]!!.ownerPlayerId)
    }
}
