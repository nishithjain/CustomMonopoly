package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.UndoAuthorizationController
import com.boardbanker.app.banking.UndoAuthorizationPhase
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
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
class UndoAuthorizationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var audio: RecordingGameAudioFeedback

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
        audio = RecordingGameAudioFeedback()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startActiveGame(playerIds: List<String> = listOf("USR_01", "USR_02")) {
        var session = (sessionManager.createNewGame() as ProcessCommitResult.Committed).session
        playerIds.forEach { playerId ->
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
            ).session
        }
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private suspend fun makeUndoAvailable() {
        sessionManager.processCommand(sessionManager.currentSession()!!, GameCommand.PayGoSalary("USR_01"))
    }

    private fun createViewModel(): AdvancedBankingViewModel =
        AdvancedBankingViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = audio,
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun tappingUndoOpensAuthorizationInsteadOfExecuting() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val before = sessionManager.currentSession()!!
        val viewModel = createViewModel()
        viewModel.onUndo()
        advanceUntilIdle()

        assertEquals(AdvancedBankingStep.UndoAuthorization, viewModel.uiState.value.step)
        assertEquals(UndoAuthorizationPhase.COLLECTING, viewModel.uiState.value.authorization.phase)
        assertEquals(before.players["USR_01"]!!.balance, sessionManager.currentSession()!!.players["USR_01"]!!.balance)
        assertNotNull(sessionManager.currentSession()!!.undoSnapshot)
        assertFalse(sessionManager.currentSession()!!.transactions.any { it.transactionType == TransactionType.UNDO })
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
        assertFalse(audio.gameplayCalls.contains("UNDO"))
    }

    @Test
    fun nothingToUndoDoesNotOpenAuthorizationOrPlayUndoSound() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        viewModel.onUndo()
        advanceUntilIdle()

        assertEquals(AdvancedBankingStep.Hub, viewModel.uiState.value.step)
        assertEquals(UndoAuthorizationController.NOTHING_TO_UNDO_MESSAGE, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.authorization.active)
        assertEquals(1, audio.errorCalls.size)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun validScanVerifiesPlayerAndDoesNotPlayUndoSound() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        advanceUntilIdle()

        val authorization = viewModel.uiState.value.authorization
        assertEquals(1, authorization.verifiedCount)
        assertTrue(authorization.players.first { it.playerId == "USR_01" }.verified)
        assertEquals(listOf("USR_02"), authorization.waitingPlayers.map { it.playerId })
        assertNull(sessionManager.currentSession()!!.transactions.lastOrNull { it.transactionType == TransactionType.UNDO })
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun duplicateScanShowsAlreadyApprovedAndKeepsCount() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        viewModel.onScanDelivered("USR_01", CardType.USER)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.authorization.verifiedCount)
        assertEquals("Nishith has already approved the undo.", viewModel.uiState.value.message)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun propertyAndEventCardsAreRejectedDuringAuthorization() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("PRP_01", CardType.PROPERTY)
        assertEquals(UndoAuthorizationController.WRONG_CARD_MESSAGE, viewModel.uiState.value.message)
        viewModel.dismissMessage()
        viewModel.onScanDelivered("EVT_01", CardType.EVENT)
        assertEquals(UndoAuthorizationController.WRONG_CARD_MESSAGE, viewModel.uiState.value.message)
        assertEquals(0, viewModel.uiState.value.authorization.verifiedCount)
        assertNotNull(sessionManager.currentSession()!!.undoSnapshot)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun unregisteredPlayerPlaysErrorSound() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_04", CardType.USER)
        advanceUntilIdle()

        assertEquals(UndoAuthorizationController.UNREGISTERED_PLAYER_MESSAGE, viewModel.uiState.value.message)
        assertEquals(1, audio.errorCalls.size)
        assertEquals(0, viewModel.uiState.value.authorization.verifiedCount)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun finalScanExecutesUndoOnceAndPlaysNewSound() = runTest {
        startActiveGame()
        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        viewModel.onScanDelivered("USR_02", CardType.USER)
        advanceUntilIdle()

        assertEquals(before, sessionManager.currentSession()!!.players["USR_01"]!!.balance)
        assertEquals(1, sessionManager.currentSession()!!.transactions.count { it.transactionType == TransactionType.UNDO })
        assertEquals(AdvancedBankingStep.Hub, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.authorization.active)
        assertEquals(UndoAuthorizationController.SUCCESS_MESSAGE, viewModel.uiState.value.result?.primaryMessage)
        assertEquals(listOf("UNDO_LAST_ACTION"), audio.gameplayCalls.filter { it == "UNDO_LAST_ACTION" || it == "UNDO" })
    }

    @Test
    fun rapidDuplicateFinalScanCannotExecuteUndoTwice() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        viewModel.onScanDelivered("USR_02", CardType.USER)
        viewModel.onScanDelivered("USR_02", CardType.USER)
        viewModel.onScanDelivered("USR_01", CardType.USER)
        advanceUntilIdle()

        assertEquals(1, sessionManager.currentSession()!!.transactions.count { it.transactionType == TransactionType.UNDO })
        assertEquals(1, audio.gameplayCalls.count { it == "UNDO_LAST_ACTION" })
        assertFalse(audio.gameplayCalls.contains("UNDO"))
    }

    @Test
    fun cancelClearsProgressWithoutConsumingSnapshotOrPlayingSound() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val snapshot = sessionManager.currentSession()!!.undoSnapshot
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        viewModel.onCancelUndo()
        advanceUntilIdle()

        assertEquals(AdvancedBankingStep.Hub, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.authorization.active)
        assertEquals(0, viewModel.uiState.value.authorization.verifiedCount)
        assertEquals(snapshot, sessionManager.currentSession()!!.undoSnapshot)
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(sessionManager.currentSession()!!.transactions.any { it.transactionType == TransactionType.UNDO })
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun androidBackCancelsAuthorization() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onBack()
        advanceUntilIdle()

        assertEquals(AdvancedBankingStep.Hub, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.authorization.active)
        assertNotNull(sessionManager.currentSession()!!.undoSnapshot)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
    }

    @Test
    fun failedUndoDoesNotPlayUndoLastActionOrRetry() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        sessionManager.processCommand(
            sessionManager.currentSession()!!,
            GameCommand.ApplyEvent("EVT_13", "USR_01"),
        )
        viewModel.onScanDelivered("USR_02", CardType.USER)
        advanceUntilIdle()

        assertEquals(AdvancedBankingStep.UndoAuthorization, viewModel.uiState.value.step)
        assertEquals(UndoAuthorizationPhase.FAILED, viewModel.uiState.value.authorization.phase)
        assertFalse(audio.gameplayCalls.contains("UNDO_LAST_ACTION"))
        val undoCount = sessionManager.currentSession()!!.transactions.count { it.transactionType == TransactionType.UNDO }
        viewModel.onScanDelivered("USR_01", CardType.USER)
        viewModel.onScanDelivered("USR_02", CardType.USER)
        advanceUntilIdle()
        assertEquals(undoCount, sessionManager.currentSession()!!.transactions.count { it.transactionType == TransactionType.UNDO })
        assertEquals(UndoAuthorizationPhase.FAILED, viewModel.uiState.value.authorization.phase)
    }

    @Test
    fun scannerRoutingResumesAfterCancel() = runTest {
        startActiveGame()
        makeUndoAvailable()
        val viewModel = createViewModel()
        viewModel.onUndo()
        viewModel.onCancelUndo()
        viewModel.onCollectGo()
        viewModel.onScanDelivered("USR_01", CardType.USER)
        advanceUntilIdle()

        val step = viewModel.uiState.value.step
        assertTrue(step is AdvancedBankingStep.GoConfirm)
        assertEquals("USR_01", (step as AdvancedBankingStep.GoConfirm).playerId)
    }
}
