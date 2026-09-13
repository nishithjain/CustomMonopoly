package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FestivalContributionDebtTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun insufficientCashOpensStructuredEventDebtWithoutPartialPayments() {
        val session = festivalSession(balance = 4000, playerCount = 4)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        val debt = result.session.debtResolution!!
        assertEquals(DebtReason.EVENT, debt.reason)
        assertNotNull(debt.eventDebt)
        assertEquals("EVT_06", debt.eventDebt!!.eventId)
        assertEquals("Festival Contribution", debt.eventDebt!!.eventName)
        assertEquals(5000, debt.eventDebt!!.amountPerRecipient)
        assertEquals(listOf("USR_02", "USR_03", "USR_04"), debt.eventDebt!!.recipientPlayerIds)
        assertEquals(15000, debt.eventDebt!!.totalAmountDue)
        assertEquals(4000, debt.eventDebt!!.cashAvailable)
        assertEquals(11000, debt.eventDebt!!.shortfall)
        assertEquals(11000, debt.amountRemaining)
        assertEquals(4000, result.session.players["USR_01"]!!.balance)
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertNotNull(result.session.pendingEventExecution)
        assertTrue(result.session.players.values.none { it.balance < 0 })
    }

    @Test
    fun assetSaleCoversShortfallAndPaysEveryRecipientExactlyOnce() {
        var session = festivalSession(balance = 4000, playerCount = 4)
        session = session.copy(
            properties = session.properties + (
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01")).session
        val beforeOthers = listOf("USR_02", "USR_03", "USR_04").associateWith {
            session.players[it]!!.balance
        }
        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))

        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertEquals(1000, settled.session.players["USR_01"]!!.balance)
        beforeOthers.forEach { (playerId, before) ->
            assertEquals(before + 5000, settled.session.players[playerId]!!.balance)
        }
        assertEquals(
            3,
            settled.session.transactions.count { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER },
        )
        assertEquals(
            1,
            settled.session.transactions.count { it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER },
        )
        assertTrue(settled.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
        assertTrue(settled.session.players.values.none { it.balance < 0 })
    }

    @Test
    fun partialAssetSaleDoesNotPayRecipients() {
        var session = festivalSession(balance = 4000, playerCount = 4)
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01")).session
        val beforeOthers = listOf("USR_02", "USR_03", "USR_04").map { session.players[it]!!.balance }
        val partial = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_01")))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, partial.outcome)
        assertNotNull(partial.session.debtResolution)
        assertEquals(5000, partial.session.debtResolution!!.amountRemaining)
        assertEquals(10000, partial.session.players["USR_01"]!!.balance)
        assertEquals(beforeOthers, listOf("USR_02", "USR_03", "USR_04").map { partial.session.players[it]!!.balance })
        assertFalse(partial.session.transactions.any { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
    }

    @Test
    fun insufficientTotalAssetsTriggerBankruptcy() {
        val session = engine.process(
            festivalSession(balance = 4000, playerCount = 3),
            GameCommand.ApplyEvent("EVT_06", "USR_01"),
        ).session
        val bankrupt = engine.process(
            session,
            GameCommand.CheckBankruptcy(
                eventResolutionId = session.pendingEventResolution!!.resolutionId,
                debtId = session.debtResolution!!.eventDebt!!.debtId,
                debtorPlayerId = session.debtResolution!!.debtorPlayerId,
            ),
        )

        assertEquals(GameOutcome.BANKRUPTCY, bankrupt.outcome)
        assertNull(bankrupt.session.debtResolution)
        assertTrue(bankrupt.session.players["USR_01"]!!.bankrupt)
        val bankruptcyTx = bankrupt.session.transactions.last { it.transactionType == TransactionType.BANKRUPTCY }
        assertEquals("EVT_06", bankruptcyTx.eventId)
        assertFalse(bankrupt.session.transactions.any { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
    }

    @Test
    fun luckyDrawPathOpensEventDebtWithoutPartialPayments() {
        var session = festivalSession(balance = 4000, playerCount = 3)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val result = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_06", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(DebtReason.EVENT, result.session.debtResolution!!.reason)
        assertEquals(10000, result.session.debtResolution!!.eventDebt!!.totalAmountDue)
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
    }

    @Test
    fun luckyDrawPathCanDeclareBankruptcyWithBoundEventDebt() {
        var session = festivalSession(balance = 4000, playerCount = 3)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val applied = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_06", "USR_01")).session
        val debt = applied.debtResolution!!
        val bankrupt = engine.process(
            applied,
            GameCommand.CheckBankruptcy(
                eventResolutionId = applied.pendingEventResolution!!.resolutionId,
                debtId = debt.eventDebt!!.debtId,
                debtorPlayerId = debt.debtorPlayerId,
            ),
        )

        assertEquals(GameOutcome.BANKRUPTCY, bankrupt.outcome)
        assertTrue(bankrupt.session.players["USR_01"]!!.bankrupt)
        assertNull(bankrupt.session.debtResolution)
    }

    @Test
    fun emptyAssetSelectionDoesNotMutateSession() {
        val session = engine.process(
            festivalSession(balance = 4000, playerCount = 3),
            GameCommand.ApplyEvent("EVT_06", "USR_01"),
        ).session
        val rejected = engine.process(session, GameCommand.ResolveDebtWithProperties(emptyList()))

        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertNotNull(rejected.session.debtResolution)
        assertEquals(4000, rejected.session.players["USR_01"]!!.balance)
        assertFalse(rejected.session.transactions.any { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE })
    }

    @Test
    fun eventDebtSnapshotRoundTripsThroughSerializer() {
        val session = engine.process(
            festivalSession(balance = 4000, playerCount = 3),
            GameCommand.ApplyEvent("EVT_06", "USR_01"),
        ).session
        val restored = serializer.deserialize(serializer.serialize(session))
        val eventDebt = restored.debtResolution!!.eventDebt!!
        assertEquals("Festival Contribution", eventDebt.eventName)
        assertEquals(2, eventDebt.recipientPlayerIds.size)
        assertEquals(10000, eventDebt.totalAmountDue)
    }

    @Test
    fun sufficientCashStillPaysAllRecipientsWithoutDebt() {
        val session = festivalSession(balance = 50000, playerCount = 4)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertNull(result.session.debtResolution)
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(
            result.session.transactions.single { it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER },
        )!!
        assertEquals(3, metadata.transfers.size)
        assertEquals(15000, metadata.totalAmount)
    }

    private fun festivalSession(balance: Int, playerCount: Int): com.boardbanker.core.model.GameSession {
        val players = when (playerCount) {
            2 -> listOf("USR_01", "USR_02")
            3 -> listOf("USR_01", "USR_02", "USR_03")
            else -> listOf("USR_01", "USR_02", "USR_03", "USR_04")
        }
        val balances = players.associateWith { if (it == "USR_01") balance else 10000 }
        return TestFixtures.newGameForEdition(EditionIds.INDIA, players, balances)
    }
}
