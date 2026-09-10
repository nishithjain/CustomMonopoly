package com.boardbanker.app.ui.screens.auction

import androidx.lifecycle.SavedStateHandle
import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class AuctionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val definitions = AppTestSupport.definitions
    private lateinit var sessionManager: com.boardbanker.app.game.ActiveGameSessionManager
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

    private fun createViewModel(
        propertyId: String = "PRP_03",
        startedByPlayerId: String = "USR_01",
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): AuctionViewModel = AuctionViewModel(
        sessionManager = sessionManager,
        definitions = definitions,
        propertyId = propertyId,
        startedByPlayerId = startedByPlayerId,
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun auctionStartsWithConfiguredDuration() = runTest {
        startActiveGame()
        assertEquals(15, definitions.rules.auction.timedAuctionSeconds)

        val viewModel = createViewModel()

        assertEquals(definitions.rules.auction.timedAuctionSeconds, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun onDone_emitsNavigateToActiveGame() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))
        executor.execute(GameCommand.PlaceAuctionBid("USR_01", 20))

        val viewModel = createViewModel()
        runCurrent()
        executor.execute(GameCommand.CompleteAuction)
        runCurrent()

        val events = mutableListOf<AuctionEvent>()
        val collector = launch { viewModel.events.collect { events.add(it) } }
        runCurrent()

        viewModel.onDone()
        advanceUntilIdle()
        collector.cancel()

        assertEquals(AuctionEvent.NavigateToActiveGame, events.last())
    }

    @Test
    fun bidRejectedAfterAuctionCompletes() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))
        executor.execute(GameCommand.PlaceAuctionBid("USR_01", 20))
        executor.execute(GameCommand.CompleteAuction)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBidRequested()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.awaitingBidScan)
    }

    @Test
    fun timerResumesFromSavedStateAfterRecreation() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))

        val savedState = SavedStateHandle()
        val endsAt = System.currentTimeMillis() + definitions.rules.auction.timedAuctionSeconds * 1_000L
        savedState["auction_ends_at_PRP_03"] = endsAt

        createViewModel(savedStateHandle = savedState)
        val persistedEndsAt = savedState.get<Long>("auction_ends_at_PRP_03")
        assertNotNull(persistedEndsAt)
        val endsAtEpochMs = persistedEndsAt!!

        val recreated = createViewModel(savedStateHandle = savedState)
        val expectedRemaining = ((endsAtEpochMs - System.currentTimeMillis()) / 1_000L)
            .toInt()
            .coerceAtLeast(0)

        assertEquals(endsAtEpochMs, savedState.get<Long>("auction_ends_at_PRP_03"))
        assertEquals(expectedRemaining, recreated.uiState.value.remainingSeconds)
    }

    @Test
    fun noBidAuctionLeavesPropertyUnowned() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))
        executor.execute(GameCommand.CancelAuction)

        assertNull(sessionManager.currentSession()!!.properties["PRP_03"]!!.ownerPlayerId)
        assertNull(sessionManager.currentSession()!!.auction)
    }

    @Test
    fun completeAuction_appliesOwnershipAndPaymentOnce() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))
        executor.execute(GameCommand.PlaceAuctionBid("USR_01", 20))
        val balanceBefore = sessionManager.currentSession()!!.players["USR_01"]!!.balance

        val outcome = executor.execute(GameCommand.CompleteAuction) as BankingCommitOutcome.Success
        assertEquals("USR_01", outcome.session.properties["PRP_03"]!!.ownerPlayerId)
        assertEquals(balanceBefore - 20, outcome.session.players["USR_01"]!!.balance)

        val duplicate = executor.execute(GameCommand.CompleteAuction)
        assertTrue(duplicate is BankingCommitOutcome.Rejected)
        assertEquals(balanceBefore - 20, sessionManager.currentSession()!!.players["USR_01"]!!.balance)
        assertEquals("USR_01", sessionManager.currentSession()!!.properties["PRP_03"]!!.ownerPlayerId)
    }
}
