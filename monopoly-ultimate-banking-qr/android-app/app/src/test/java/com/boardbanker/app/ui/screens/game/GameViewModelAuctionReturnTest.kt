package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.navigation.ActiveGameHubReturnSignal
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAuctionReturnTest {
    private val testDispatcher = StandardTestDispatcher()
    private val ukDefinitions = AppTestSupport.editionRepository.load(EditionIds.UK)
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
    private lateinit var activeGameHubReturnSignal: ActiveGameHubReturnSignal
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        activeGameHubReturnSignal = ActiveGameHubReturnSignal()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startUkGame(activePlayerId: String = "USR_01") {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
        if (activePlayerId != "USR_01") {
            session = sessionManager.currentSession()!!
            sessionManager.processCommand(session, GameCommand.EndTurn("USR_01"))
        }
    }

    private fun createViewModel(): GameViewModel = GameViewModel(
        sessionManager = sessionManager,
        definitions = ukDefinitions,
        transientWorkflow = TransientScanWorkflowHolder(),
        locationWorkflowHolder = LocationWorkflowHolder(),
        activeGameHubReturnSignal = activeGameHubReturnSignal,
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    @Test
    fun returnToActiveGameHub_clearsUnownedPropertyWorkflow() = runTest {
        startUkGame()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.UnownedPropertyDecision)

        viewModel.returnToActiveGameHub()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertNull(viewModel.uiState.value.cardPresentation)
        assertNull(viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.scanRequest)
    }

    @Test
    fun activeGameHubReturnSignal_clearsPropertyWorkflow() = runTest {
        startUkGame()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.UnownedPropertyDecision)

        activeGameHubReturnSignal.requestReturnToActiveGameHub()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertNull(viewModel.uiState.value.cardPresentation)
    }

    @Test
    fun returnToActiveGameHub_preservesCompletedAuctionOwnership() = runTest {
        startUkGame()
        var session = sessionManager.currentSession()!!
        session = (sessionManager.processCommand(
            session,
            GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"),
        ) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.PlaceAuctionBid("USR_01", 20)) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.CompleteAuction)

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.UnownedPropertyDecision)

        viewModel.returnToActiveGameHub()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertNull(viewModel.uiState.value.cardPresentation)
        assertEquals("USR_01", sessionManager.currentSession()!!.properties["PRP_03"]!!.ownerPlayerId)
    }
}
