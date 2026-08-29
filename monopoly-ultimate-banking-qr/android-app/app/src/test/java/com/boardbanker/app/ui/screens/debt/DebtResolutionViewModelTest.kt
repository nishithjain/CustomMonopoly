package com.boardbanker.app.ui.screens.debt

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EntityRef
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
class DebtResolutionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var store: CommittedGameSessionStore
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameSessionRepository()
        store = CommittedGameSessionStore(repository)
        sessionManager = ActiveGameSessionManager(
            definitions = AppTestSupport.definitions,
            committedStore = store,
            repository = repository,
            engine = AppTestSupport.engine,
        )
        audio = RecordingGameAudioFeedback()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startDebtScenario(
        debtorId: String = "USR_02",
        creditorId: String = "USR_01",
        amountRemaining: Int = 500,
        ownedPropertyIds: List<String> = listOf("PRP_10", "PRP_11", "PRP_12"),
        playerBalances: Map<String, Int> = emptyMap(),
    ) {
        var session = AppTestSupport.newGame()
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    in ownedPropertyIds -> state.copy(ownerPlayerId = debtorId, currentRentLevel = 4)
                    "PRP_01" -> state.copy(ownerPlayerId = creditorId, currentRentLevel = 4)
                    else -> state
                }
            },
            players = session.players.mapValues { (id, player) ->
                player.copy(balance = playerBalances[id] ?: if (id == debtorId) 0 else player.balance)
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = debtorId,
                creditorPlayerId = creditorId,
                amountRemaining = amountRemaining,
            ),
        )
        store.commitGameResult(GameResult(session = session))
    }

    private fun createViewModel(): DebtResolutionViewModel =
        DebtResolutionViewModel(
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            gameAudioFeedback = audio,
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun initialStateMatchesOutstandingDebtAndZeroSelection() = runTest {
        startDebtScenario()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(500, state.outstandingAmount)
        assertEquals(0, state.selectedPropertyCount)
        assertEquals(0, state.selectedPropertyValue)
        assertEquals(500, state.remainingDue)
        assertTrue(state.selectedPropertyIds.isEmpty())
        assertFalse(state.settlementSummary.isSettleEnabled)
    }

    @Test
    fun togglingPropertyUpdatesSummaryImmediately() = runTest {
        startDebtScenario()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_11")
        val afterOne = viewModel.uiState.value
        assertEquals(setOf("PRP_11"), afterOne.selectedPropertyIds)
        assertEquals(1, afterOne.selectedPropertyCount)
        assertEquals(200, afterOne.selectedPropertyValue)
        assertEquals(300, afterOne.remainingDue)
        assertEquals("Settle with selected property", afterOne.settlementSummary.settleButtonLabel)

        viewModel.onToggleProperty("PRP_10")
        val afterTwo = viewModel.uiState.value
        assertEquals(setOf("PRP_10", "PRP_11"), afterTwo.selectedPropertyIds)
        assertEquals(2, afterTwo.selectedPropertyCount)
        assertEquals(380, afterTwo.selectedPropertyValue)
        assertEquals(120, afterTwo.remainingDue)
        assertEquals("Settle with selected properties", afterTwo.settlementSummary.settleButtonLabel)
    }

    @Test
    fun togglingSamePropertyTwiceDoesNotDuplicateSelection() = runTest {
        startDebtScenario()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_11")
        viewModel.onToggleProperty("PRP_11")
        viewModel.onToggleProperty("PRP_11")

        val state = viewModel.uiState.value
        assertEquals(setOf("PRP_11"), state.selectedPropertyIds)
        assertEquals(1, state.selectedPropertyCount)
        assertEquals(200, state.selectedPropertyValue)
    }

    @Test
    fun refreshPreservesValidSelectionAcrossRecomposition() = runTest {
        startDebtScenario()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onToggleProperty("PRP_11")
        viewModel.refreshFromSession()

        val state = viewModel.uiState.value
        assertEquals(setOf("PRP_10", "PRP_11"), state.selectedPropertyIds)
        assertEquals(380, state.selectedPropertyValue)
        assertEquals(120, state.remainingDue)
    }

    @Test
    fun settlementSummaryMatchesEngineForFiveHundredDebtScenario() = runTest {
        startDebtScenario(
            amountRemaining = 500,
            ownedPropertyIds = listOf("PRP_10", "PRP_01"),
            playerBalances = mapOf("USR_02" to 0),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(500, viewModel.uiState.value.settlementSummary.outstandingAmount)
        assertEquals("0 properties", viewModel.uiState.value.settlementSummary.propertiesSelectedLabel)
        assertEquals(0, viewModel.uiState.value.settlementSummary.selectedPropertyValue)
        assertEquals(500, viewModel.uiState.value.settlementSummary.remainingDue)

        viewModel.onToggleProperty("PRP_10")
        assertEquals(180, viewModel.uiState.value.settlementSummary.selectedPropertyValue)
        assertEquals(320, viewModel.uiState.value.settlementSummary.remainingDue)

        viewModel.onToggleProperty("PRP_01")
        val purchasePrices = AppTestSupport.definitions.properties
        val expectedTotal = purchasePrices["PRP_10"]!!.purchasePrice + purchasePrices["PRP_01"]!!.purchasePrice
        assertEquals(2, viewModel.uiState.value.settlementSummary.selectedPropertyCount)
        assertEquals(expectedTotal, viewModel.uiState.value.settlementSummary.selectedPropertyValue)
        assertEquals(500 - expectedTotal, viewModel.uiState.value.settlementSummary.remainingDue)
    }

    @Test
    fun settlingOverpaymentReturnsChangeFromCreditor() = runTest {
        startDebtScenario(
            amountRemaining = 160,
            ownedPropertyIds = listOf("PRP_10"),
            playerBalances = mapOf("USR_01" to 1200, "USR_02" to 0),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onSettleSelected()
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        assertEquals(20, session.players["USR_02"]!!.balance)
        assertEquals(1180, session.players["USR_01"]!!.balance)
        assertNull(session.debtResolution)
        assertEquals("USR_01", session.properties["PRP_10"]!!.ownerPlayerId)
    }

    @Test
    fun settlingSelectedPropertiesTransfersExactlyThoseProperties() = runTest {
        startDebtScenario(amountRemaining = 500)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onToggleProperty("PRP_11")
        viewModel.onSettleSelected()
        advanceUntilIdle()

        val session = sessionManager.currentSession()!!
        assertEquals("USR_01", session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals("USR_01", session.properties["PRP_11"]!!.ownerPlayerId)
        assertEquals(120, session.debtResolution!!.amountRemaining)
        assertTrue(viewModel.uiState.value.selectedPropertyIds.isEmpty())
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun settlingExactCoverageClearsDebtAndSelection() = runTest {
        startDebtScenario(amountRemaining = 380)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onToggleProperty("PRP_11")
        viewModel.onSettleSelected()
        advanceUntilIdle()

        assertNull(sessionManager.currentSession()!!.debtResolution)
        assertTrue(viewModel.uiState.value.selectedPropertyIds.isEmpty())
        assertNotNull(viewModel.uiState.value.result)
    }

    @Test
    fun displayedRemainingDueMatchesEngineOutstandingAfterPartialSettlement() = runTest {
        startDebtScenario(amountRemaining = 500)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_11")
        assertEquals(
            sessionManager.currentSession()!!.debtResolution!!.amountRemaining - 200,
            viewModel.uiState.value.remainingDue,
        )
    }

    @Test
    fun coveredMessageAppearsWhenRemainingDueReachesZero() = runTest {
        startDebtScenario(amountRemaining = 380)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onToggleProperty("PRP_11")

        assertEquals(
            "The selected property value covers the amount due.",
            viewModel.uiState.value.settlementSummary.selectionGuidance,
        )
        assertEquals(0, viewModel.uiState.value.remainingDue)
    }

    @Test
    fun refreshWithoutSettlementDoesNotChangeOwnershipOrDebt() = runTest {
        startDebtScenario()
        val before = sessionManager.currentSession()!!
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_11")
        viewModel.refreshFromSession()

        val after = sessionManager.currentSession()!!
        assertEquals(before.debtResolution, after.debtResolution)
        assertEquals(before.properties["PRP_11"]!!.ownerPlayerId, after.properties["PRP_11"]!!.ownerPlayerId)
    }

    @Test
    fun bankDebtScenarioUsesPurchasePriceForSelectedValue() = runTest {
        var session = AppTestSupport.newGame()
        session = session.copy(
            properties = session.properties + (
                "PRP_11" to session.properties["PRP_11"]!!.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
            ),
            players = session.players.mapValues { (id, player) ->
                if (id == "USR_01") player.copy(balance = 0) else player
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 500,
            ),
        )
        store.commitGameResult(GameResult(session = session))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_11")
        assertEquals(
            AppTestSupport.definitions.properties["PRP_11"]!!.purchasePrice,
            viewModel.uiState.value.selectedPropertyValue,
        )
    }
}
