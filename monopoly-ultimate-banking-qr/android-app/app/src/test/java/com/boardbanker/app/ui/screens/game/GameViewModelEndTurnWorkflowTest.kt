package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.navigation.ActiveGameHubReturnSignal
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.screens.history.HistoryDetail
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
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
class GameViewModelEndTurnWorkflowTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
    private lateinit var audio: RecordingGameAudioFeedback
    private lateinit var viewModel: GameViewModel
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
        audio = RecordingGameAudioFeedback()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startIndiaGame(playerIds: List<String> = listOf("USR_01", "USR_02", "USR_03")) {
        var session = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in playerIds) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private fun createViewModel(): GameViewModel =
        GameViewModel(
            sessionManager = sessionManager,
            definitions = definitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            activeGameHubReturnSignal = ActiveGameHubReturnSignal(),
            gameAudioFeedback = audio,
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun endTurn_staysOnActiveGameHubWithoutDoneScreen() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.result)
        assertEquals(GameplayWorkflowState.Ready, viewModel.uiState.value.workflowState)
        assertFalse(viewModel.uiState.value.commandInFlight)
        assertTrue(viewModel.uiState.value.actionAvailability.endTurnEnabled)
    }

    @Test
    fun endTurn_highlightsNextEligiblePlayer() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals("USR_02", viewModel.uiState.value.activePlayerId)
        val activeDashboard = viewModel.uiState.value.players.single { it.isActiveTurn }
        assertEquals("USR_02", activeDashboard.playerId)
        assertEquals("Aditya", activeDashboard.playerName)
    }

    @Test
    fun repeatedEndTurn_advancesTurnExactlyOncePerTap() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02", "USR_03"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()
        assertEquals("USR_02", sessionManager.currentSession()!!.turnState!!.activePlayerId)

        viewModel.onEndTurn()
        advanceUntilIdle()
        assertEquals("USR_03", sessionManager.currentSession()!!.turnState!!.activePlayerId)

        val turnAdvanceCount = sessionManager.currentSession()!!.transactions
            .count { it.transactionType == TransactionType.TURN_ADVANCED }
        assertEquals(2, turnAdvanceCount)
    }

    @Test
    fun endTurn_cannotReopenRemovedDoneScreenViaUiState() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()
        viewModel.onDone()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.result)
        assertEquals("USR_02", viewModel.uiState.value.activePlayerId)
    }

    @Test
    fun endTurn_blockedWhileDebtSettlementPending() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        val session = sessionManager.currentSession()!!.copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 1_000,
                reason = DebtReason.EVENT,
            ),
        )
        repository.save(session)
        sessionManager.restoreFromStorage()

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        assertEquals("USR_01", sessionManager.currentSession()!!.turnState!!.activePlayerId)
        assertFalse(
            sessionManager.currentSession()!!.transactions.any { it.transactionType == TransactionType.TURN_ADVANCED },
        )
    }

    @Test
    fun endTurn_blockedWhileLuckyDrawPending() = runTest {
        startIndiaGame()
        val session = sessionManager.currentSession()!!
        sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_15", "USR_01"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        assertNotNull(sessionManager.currentSession()!!.pendingEventDraw)
        assertEquals("USR_01", sessionManager.currentSession()!!.turnState!!.activePlayerId)
        assertNull(viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.actionAvailability.endTurnEnabled)
    }

    @Test
    fun endTurn_recordsRecentBankingAndPlaysTurnChangedOnce() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        val entries = TransactionHistoryEntries.build(sessionManager.currentSession()!!, definitions)
        val turnEnded = entries.first { it.title == TransactionHistoryEntries.TURN_ENDED_TITLE }
            .detail as HistoryDetail.PlayerMention
        val nextTurn = entries.first { it.title == "Next turn" }
        val nextTurnDetail = nextTurn.detail as HistoryDetail.PlayerMention

        assertEquals("USR_01", turnEnded.playerId)
        assertEquals("Nishith", turnEnded.playerName)
        assertEquals("USR_02", nextTurnDetail.playerId)
        assertEquals("Aditya", nextTurnDetail.playerName)
        assertEquals(CommonUiIcon.CURRENT_TURN, nextTurn.entryIcon)
        assertEquals(listOf("TURN_CHANGED"), audio.gameplayCalls)
    }

    @Test
    fun endTurn_autosaveResumeAndUndoRemainCorrect() = runTest {
        startIndiaGame(listOf("USR_01", "USR_02"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEndTurn()
        advanceUntilIdle()

        val afterEndTurn = sessionManager.currentSession()!!
        repository.save(afterEndTurn)
        val restored = serializer.deserialize(serializer.serialize(afterEndTurn))
        assertEquals("USR_02", restored.turnState!!.activePlayerId)

        val resumedViewModel = createViewModel()
        advanceUntilIdle()
        assertEquals("USR_02", resumedViewModel.uiState.value.activePlayerId)
        assertNull(resumedViewModel.uiState.value.result)

        sessionManager.processCommand(
            sessionManager.currentSession()!!,
            GameCommand.UndoLastAction,
        )
        val afterUndoViewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("USR_01", sessionManager.currentSession()!!.turnState!!.activePlayerId)
        assertEquals("USR_01", afterUndoViewModel.uiState.value.activePlayerId)
        assertNull(afterUndoViewModel.uiState.value.result)
    }
}
