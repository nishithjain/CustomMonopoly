package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.screens.debt.DebtResolutionViewModel
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
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
class DebtSettlementHistoryTests {
    private val definitions = AppTestSupport.definitions
    private val engine = AppTestSupport.engine
    private val serializer = KotlinGameSessionSerializer()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameSessionRepository
    private lateinit var store: CommittedGameSessionStore
    private lateinit var sessionManager: ActiveGameSessionManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameSessionRepository()
        val (manager, committedStore) = AppTestSupport.sessionManagerWithStore(repository)
        sessionManager = manager
        store = committedStore
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recentBankingShowsPropertyActionsAndFinalSettlement() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            players = session.players.mapValues { (id, player) ->
                when (id) {
                    "USR_01" -> player.copy(balance = 1500)
                    "USR_02" -> player.copy(balance = 0)
                    else -> player
                }
            },
        )
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
                    "PRP_10" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
                    else -> state
                }
            },
        )
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_12")).session
        session = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10"))).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val propertyEntry = entries.first { it.title.startsWith("Property Transferred") }
        val settlementEntry = entries.first { it.title == "Rent Debt Settled" }

        assertTrue(propertyEntry.title.contains("PRP_10") || propertyEntry.title.contains("Old Kent"))
        val propertyDetail = propertyEntry.detail as HistoryDetail.DebtPropertySettlement
        assertEquals("USR_02", (propertyDetail.transfer.from as DisplayIdentity.Player).playerId)
        assertEquals("USR_01", (propertyDetail.transfer.to as DisplayIdentity.Player).playerId)
        assertTrue(propertyDetail.valueLabel.startsWith("Settlement value:"))

        val settlementDetail = settlementEntry.detail as HistoryDetail.RentDebtSettled
        assertEquals("USR_02", (settlementDetail.transfer.from as DisplayIdentity.Player).playerId)
        assertEquals("USR_01", (settlementDetail.transfer.to as DisplayIdentity.Player).playerId)
        assertTrue(settlementDetail.cashUsed.isNotBlank())
        assertTrue(settlementDetail.propertyValueUsed.isNotBlank())
    }

    @Test
    fun bankSaleShowsPropertySoldEntry() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            players = session.players.mapValues { (id, player) ->
                if (id == "USR_01") player.copy(balance = 0) else player
            },
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_11" to session.properties["PRP_11"]!!.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
            ),
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 160,
            ),
        )
        session = engine.process(session, GameCommand.ResolveDebt("PRP_11")).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val soldEntry = entries.first { it.title.startsWith("Property Sold") }
        val settlementEntry = entries.first { it.title == "Rent Debt Settled" }

        assertTrue((soldEntry.detail as HistoryDetail.DebtPropertySettlement).valueLabel.startsWith("Sale value:"))
        assertEquals(DisplayIdentity.Bank, (soldEntry.detail as HistoryDetail.DebtPropertySettlement).transfer.to)
        assertNotNull(settlementEntry)
    }

    @Test
    fun failedSettlementCreatesNoRentDebtSettledRecord() {
        val session = AppTestSupport.newGame().copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 500,
            ),
        )
        val result = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10")))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.RENT_DEBT_SETTLED })
        val entries = TransactionHistoryEntries.build(result.session, definitions)
        assertFalse(entries.any { it.title == "Rent Debt Settled" })
    }

    @Test
    fun repeatedSettlementTapDoesNotCreateDuplicateTransactions() = runTest {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            properties = base.properties.mapValues { (id, state) ->
                if (id == "PRP_10") state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1) else state
            },
            players = base.players.mapValues { (id, player) ->
                if (id == "USR_02") player.copy(balance = 0) else player
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 380,
                reason = DebtReason.RENT,
                propertyId = "PRP_12",
            ),
        )
        store.commitGameResult(GameResult(session = session))
        val viewModel = DebtResolutionViewModel(
            sessionManager = sessionManager,
            definitions = definitions,
            gameAudioFeedback = com.boardbanker.app.audio.RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = com.boardbanker.app.audio.GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        viewModel.onToggleProperty("PRP_10")
        viewModel.onSettleSelected()
        viewModel.onSettleSelected()
        advanceUntilIdle()

        val committed = sessionManager.currentSession()!!
        assertEquals(
            1,
            committed.transactions.count { it.transactionType == TransactionType.RENT_DEBT_SETTLED },
        )
        assertNotNull(viewModel.uiState.value.result)
    }

    @Test
    fun legacySavedGameJsonLoadsWithoutCrashing() {
        val session = AppTestSupport.newGame().copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 200,
            ),
        )
        val json = serializer.serialize(session).replace(",\"originalAmountDue\":200", "")
        val restored = serializer.deserialize(json)
        assertEquals(200, restored.debtResolution!!.originalAmountDue)
        assertEquals(0, restored.debtResolution!!.cashAmountUsed)
        assertNull(restored.debtResolution!!.propertyId)
    }
}
