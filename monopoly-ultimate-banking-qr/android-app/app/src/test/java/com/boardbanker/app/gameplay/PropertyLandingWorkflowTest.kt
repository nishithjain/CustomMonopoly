package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.ui.screens.game.GameViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyLandingWorkflowTest {
    private val testDispatcher = StandardTestDispatcher()
    private val ukDefinitions = AppTestSupport.editionRepository.load(EditionIds.UK)
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
    fun activePlayerScanningOwnedPropertyDoesNotRequestPlayerCard() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startUkGame(sessionManager, activePlayerId = "USR_01")
        var session = sessionManager.currentSession()!!
        session = (
            sessionManager.processCommand(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
                as ProcessCommitResult.Committed
            ).session
        session = (
            sessionManager.processCommand(session, GameCommand.EndTurn("USR_01"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = createViewModel(sessionManager, ukDefinitions)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.scanRequest)
        assertFalse(viewModel.uiState.value.workflowState is GameplayWorkflowState.WaitingForRentPayer)
        val rentTx = sessionManager.currentSession()!!.transactions.lastOrNull {
            it.transactionType == TransactionType.RENT_PAYMENT
        }
        assertNotNull(rentTx)
        assertEquals("USR_02", rentTx!!.playerId)
        assertEquals("USR_02", rentTx.fromEntity)
        assertEquals("USR_01", rentTx.toEntity)
        assertEquals("USR_02", sessionManager.currentSession()!!.turnState?.activePlayerId)
    }

    @Test
    fun activePlayerScanningUnownedPropertyShowsPurchaseWithoutPlayerScan() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startUkGame(sessionManager, activePlayerId = "USR_01")

        val viewModel = createViewModel(sessionManager, ukDefinitions)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workflowState is GameplayWorkflowState.UnownedPropertyDecision)
        assertNull(viewModel.uiState.value.scanRequest)
    }

    @Test
    fun activePlayerBuyingUnownedPropertyAssignsOwnership() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startUkGame(sessionManager, activePlayerId = "USR_01")

        val viewModel = createViewModel(sessionManager, ukDefinitions)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        viewModel.onBuyProperty()
        advanceUntilIdle()

        assertEquals("USR_01", sessionManager.currentSession()!!.properties["PRP_01"]!!.ownerPlayerId)
    }

    @Test
    fun activePlayerScanningOwnPropertyAppliesOwnerLandingWithoutPlayerScan() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        startUkGame(sessionManager, activePlayerId = "USR_01")
        var session = sessionManager.currentSession()!!
        session = (
            sessionManager.processCommand(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
                as ProcessCommitResult.Committed
            ).session
        val beforeLevel = session.properties["PRP_01"]!!.currentRentLevel

        val viewModel = createViewModel(sessionManager, ukDefinitions)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.scanRequest)
        val afterLevel = sessionManager.currentSession()!!.properties["PRP_01"]!!.currentRentLevel
        assertTrue(afterLevel >= beforeLevel)
    }

    @Test
    fun indiaEditionPropertyLandingUsesActivePlayer() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
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
        session = (
            sessionManager.processCommand(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
                as ProcessCommitResult.Committed
            ).session
        session = (
            sessionManager.processCommand(session, GameCommand.EndTurn("USR_01"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = createViewModel(sessionManager, indiaDefinitions)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.scanRequest)
        val rentTx = sessionManager.currentSession()!!.transactions.lastOrNull {
            it.transactionType == TransactionType.RENT_PAYMENT
        }
        assertNotNull(rentTx)
        assertEquals("USR_02", rentTx!!.playerId)
    }

    private fun createViewModel(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
        definitions: com.boardbanker.core.model.GameDefinitions,
    ): GameViewModel = GameViewModel(
        sessionManager = sessionManager,
        definitions = definitions,
        transientWorkflow = TransientScanWorkflowHolder(),
        locationWorkflowHolder = LocationWorkflowHolder(),
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    private suspend fun startUkGame(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
        activePlayerId: String = "USR_01",
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
    }

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
        session = (
            sessionManager.processCommand(session, GameCommand.EndTurn("USR_01"))
                as ProcessCommitResult.Committed
            ).session
    }
}
