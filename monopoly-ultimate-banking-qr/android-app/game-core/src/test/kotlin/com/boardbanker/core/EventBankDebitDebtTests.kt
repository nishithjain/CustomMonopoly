package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventBankDebitDebtTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun hospitalExpense_sufficientFundsCompleteWithoutDebt() {
        val session = indiaSession(mapOf("USR_01" to 50000))
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_01"))

        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertNull(result.session.debtResolution)
        assertNull(result.session.pendingEventExecution)
        assertEquals(before - 10000, result.session.players["USR_01"]!!.balance)
        assertTrue(result.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
    }

    @Test
    fun hospitalExpense_insufficientFundsOpensStructuredEventBankDebit() {
        val session = indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000))
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        val debt = result.session.debtResolution!!
        assertEquals(EntityRef.BANK, debt.creditorPlayerId)
        assertNotNull(debt.eventBankDebit)
        assertEquals("EVT_05", debt.eventBankDebit!!.eventId)
        assertEquals("Hospital Expense", debt.eventBankDebit!!.eventName)
        assertEquals(10000, debt.eventBankDebit!!.totalAmountDue)
        assertEquals(3000, debt.eventBankDebit!!.cashAvailable)
        assertEquals(7000, debt.eventBankDebit!!.shortfall)
        assertEquals(0, result.session.players["USR_01"]!!.balance)
        assertNotNull(result.session.pendingEventExecution)
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
    }

    @Test
    fun hospitalExpense_canDeclareBankruptcyWithBoundEventDebt() {
        val applied = engine.process(
            indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000)),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        ).session
        val debt = applied.debtResolution!!
        val eventBankDebit = debt.eventBankDebit!!
        val bankrupt = engine.process(
            applied,
            GameCommand.CheckBankruptcy(
                eventResolutionId = applied.pendingEventResolution!!.resolutionId,
                debtId = eventBankDebit.debtId,
                debtorPlayerId = debt.debtorPlayerId,
            ),
        )

        assertEquals(GameOutcome.BANKRUPTCY, bankrupt.outcome)
        assertEquals(GameStatus.FINISHED, bankrupt.session.status)
        assertTrue(bankrupt.session.players["USR_01"]!!.bankrupt)
        assertNull(bankrupt.session.debtResolution)
        assertNull(bankrupt.session.pendingEventExecution)
        assertTrue(bankrupt.session.players["USR_01"]!!.balance >= 0)
    }

    @Test
    fun hospitalExpense_assetSaleCompletesPaymentAndClearsPendingEvent() {
        var session = indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000))
        session = session.copy(
            properties = session.properties + (
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_01")).session
        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))

        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertNull(settled.session.properties["PRP_05"]!!.ownerPlayerId)
        assertTrue(settled.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
        val bankDebits = settled.session.transactions.filter { it.transactionType == TransactionType.BANK_DEBIT }
        assertEquals(10000, bankDebits.sumOf { it.amount ?: 0 })
        assertTrue(settled.session.players.values.none { it.balance < 0 })
    }

    @Test
    fun hospitalExpense_endTurnSucceedsAfterDebtResolution() {
        var session = indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000))
        session = session.copy(
            properties = session.properties + (
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_01")).session
        session = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05"))).session
        val endTurn = engine.process(session, GameCommand.EndTurn("USR_01"))

        assertEquals(GameOutcome.SUCCESS, endTurn.outcome)
        assertNull(endTurn.session.pendingEventExecution)
        assertNull(endTurn.session.debtResolution)
    }

    @Test
    fun municipalMaintenance_usesConfiguredPerPropertyAmount() {
        var session = indiaSession(mapOf("USR_01" to 50000, "USR_02" to 25000))
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
                ) + (
                "PRP_02" to session.properties["PRP_02"]!!.copy(ownerPlayerId = "USR_01")
                ),
        )
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))

        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertNull(result.session.debtResolution)
        assertEquals(before - 4000, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun municipalMaintenance_zeroObligationCompletesWithoutDebt() {
        val session = indiaSession(mapOf("USR_01" to 50000))
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))

        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertNull(result.session.debtResolution)
        assertNull(result.session.pendingEventExecution)
    }

    @Test
    fun municipalMaintenance_insufficientFundsResolveThroughEventBankDebit() {
        var session = indiaSession(mapOf("USR_01" to 1000, "USR_02" to 25000))
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertNotNull(result.session.debtResolution!!.eventBankDebit)
        assertEquals("Municipal Maintenance", result.session.debtResolution!!.eventBankDebit!!.eventName)
        assertEquals(2000, result.session.debtResolution!!.eventBankDebit!!.totalAmountDue)

        session = result.session.copy(
            properties = result.session.properties + (
                "PRP_05" to result.session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))

        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertTrue(settled.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
    }

    @Test
    fun debtResolutionDoesNotTriggerAnotherEventInProgress() {
        val session = engine.process(
            indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000)),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        ).session
        val resolved = engine.process(session, GameCommand.ResolveDebtWithProperties(emptyList()))

        assertEquals(GameOutcome.REJECTED, resolved.outcome)
        assertNotNull(resolved.session.debtResolution)
    }

    @Test
    fun eventBankDebitSnapshotRoundTripsThroughSerializer() {
        val session = engine.process(
            indiaSession(mapOf("USR_01" to 3000, "USR_02" to 25000)),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        ).session
        val restored = serializer.deserialize(serializer.serialize(session))
        val bankDebit = restored.debtResolution!!.eventBankDebit!!

        assertEquals("Hospital Expense", bankDebit.eventName)
        assertEquals(10000, bankDebit.totalAmountDue)
        assertEquals(7000, bankDebit.shortfall)
    }

    private fun indiaSession(balances: Map<String, Int>) =
        TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            balances.keys.toList(),
            balances,
        )
}
