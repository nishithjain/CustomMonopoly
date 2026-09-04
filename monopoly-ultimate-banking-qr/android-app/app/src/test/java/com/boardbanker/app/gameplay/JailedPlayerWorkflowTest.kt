package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.ui.screens.game.GameViewModel
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
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
class JailedPlayerWorkflowTest {
    private val testDispatcher = StandardTestDispatcher()
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private val controller = GameplayWorkflowController(indiaDefinitions)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun jailedActivePlayer_propertyScanDoesNotOpenBuyDecision() {
        val session = jailedActiveSession("USR_02")
        val actions = controller.onPropertyScanned("PRP_01", session)
        assertTrue(actions.any { it is WorkflowAction.StateChanged })
        assertTrue(controller.currentState() is GameplayWorkflowState.Error)
        assertFalse(controller.currentState() is GameplayWorkflowState.UnownedPropertyDecision)
    }

    @Test
    fun jailedActivePlayer_eventScanShowsJailGuidance() {
        val session = jailedActiveSession("USR_02")
        controller.onEventScanned("EVT_05", session)
        assertTrue(controller.currentState() is GameplayWorkflowState.Error)
    }

    @Test
    fun jailedActivePlayer_buySelectedIsRejected() {
        val session = jailedActiveSession("USR_02")
        controller.onPropertyScanned("PRP_01", session)
        assertTrue(controller.currentState() is GameplayWorkflowState.Error)
        val actions = controller.onBuySelected(session)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun jailResolutionGuidance_mentionsGetOutOfJail() {
        val session = jailedActiveSession("USR_02")
        val guidance = controller.jailResolutionGuidance(session)
        assertNotNull(guidance)
        assertTrue(guidance!!.contains("Player") && guidance.contains("is in Jail, Get out of Jail before continuing"))
    }

    @Test
    fun restoreWorkflowFromSession_doesNotReopenPropertyPurchaseForJailedPlayer() {
        val session = jailedActiveSession("USR_02")
        val actions = controller.restoreWorkflowFromSession(session)
        assertTrue(actions.isEmpty())
        assertTrue(controller.currentState() is GameplayWorkflowState.Ready)
    }

    @Test
    fun gameViewModel_propertyScanShowsJailGuidance() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaJailedGame(sessionManager, "USR_02")

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.actionAvailability.scanCardEnabled)

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.Ready)
        assertNotNull(viewModel.uiState.value.message)
        assertNotNull(viewModel.uiState.value.jailResolutionMessage)
        assertNull(viewModel.uiState.value.cardPresentation)
    }

    @Test
    fun gameViewModel_jailedActivePlayer_disablesScanButAllowsEndTurn() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaJailedGame(sessionManager, "USR_02")

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.actionAvailability.scanCardEnabled)
        assertTrue(viewModel.uiState.value.actionAvailability.endTurnEnabled)
        assertTrue(viewModel.uiState.value.actionAvailability.getOutOfJailEnabled)
        assertTrue(viewModel.uiState.value.actionAvailability.bankActionsEnabled)
    }

    @Test
    fun gameViewModel_jailedSessionShowsGuidanceOnLoad() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaJailedGame(sessionManager, "USR_02")

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.activePlayerInJail)
        assertNotNull(viewModel.uiState.value.jailResolutionMessage)
    }

    @Test
    fun gameViewModel_rejectedPurchaseDoesNotCreateBankingEntry() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startIndiaJailedGame(sessionManager, "USR_02")

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        viewModel.onBuyProperty()
        advanceUntilIdle()

        val current = sessionManager.currentSession()!!
        assertNull(current.properties["PRP_01"]!!.ownerPlayerId)
        assertTrue(current.players["USR_02"]!!.jailStatus)
        assertTrue(current.transactions.none { it.transactionType == TransactionType.PROPERTY_PURCHASE })
    }

    private fun createViewModel(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
    ): GameViewModel = GameViewModel(
        sessionManager = sessionManager,
        definitions = indiaDefinitions,
        transientWorkflow = TransientScanWorkflowHolder(),
        locationWorkflowHolder = LocationWorkflowHolder(),
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    private suspend fun startIndiaJailedGame(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
        activePlayerId: String,
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
        if (activePlayerId == "USR_02") {
            session = (
                sessionManager.processCommand(session, GameCommand.EndTurn("USR_01")) as ProcessCommitResult.Committed
                ).session
        }
        sessionManager.processCommand(session, GameCommand.SendPlayerToJail(activePlayerId))
    }

    @Test
    fun staleBuyDecision_rejectedAfterPlayerSentToJail() {
        var session = indiaGame()
        controller.onPropertyScanned("PRP_01", session)
        assertTrue(controller.currentState() is GameplayWorkflowState.UnownedPropertyDecision)

        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val actions = controller.onBuySelected(session)
        assertTrue(actions.any { it is WorkflowAction.StateChanged })
        assertTrue(controller.currentState() is GameplayWorkflowState.Error)
        assertTrue(actions.none { it is WorkflowAction.ExecuteCommand })
    }

    @Test
    fun propertyScan_usesPurchaseSpecificJailMessage() {
        val session = jailedActiveSession("USR_02")
        controller.onPropertyScanned("PRP_01", session)
        val error = controller.currentState() as GameplayWorkflowState.Error
        assertTrue(error.message.contains("must get out before purchasing a property"))
    }

    private fun jailedActiveSession(activePlayerId: String): GameSession {
        var session = indiaGame()
        if (activePlayerId == "USR_02") {
            session = indiaEngine.process(session, GameCommand.EndTurn("USR_01")).session
        }
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail(activePlayerId)).session
        return session
    }

    private fun indiaGame(): GameSession {
        var result = indiaEngine.process(
            GameSession(gameId = "JAIL_UI_TEST", editionId = EditionIds.INDIA),
            GameCommand.CreateGame("JAIL_UI_TEST"),
        )
        for (playerId in listOf("USR_01", "USR_02")) {
            result = indiaEngine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            )
        }
        return indiaEngine.process(result.session, GameCommand.StartGame).session
    }
}
