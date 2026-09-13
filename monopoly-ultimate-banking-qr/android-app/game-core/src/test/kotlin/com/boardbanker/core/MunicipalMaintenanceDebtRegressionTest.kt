package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventResolutionPhase
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MunicipalMaintenanceDebtRegressionTest {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun insufficientFunds_propertySaleDeductsMaintenanceOnce() {
        val amountPerProperty = 2000
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
            mapOf("USR_01" to 0, "USR_02" to 25000),
        )
        // 5 owned properties => maintenance 10_000
        val ownedIds = listOf("PRP_01", "PRP_02", "PRP_03", "PRP_04", "PRP_19")
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                if (id in ownedIds) state.copy(ownerPlayerId = "USR_01") else state
            },
        )
        val maintenance = ownedIds.size * amountPerProperty
        val salePrice = definitions.properties["PRP_19"]!!.purchasePrice

        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, applied.outcome)
        assertEquals(0, applied.session.players["USR_01"]!!.balance)
        assertNotNull(applied.session.pendingEventResolution)
        assertNotNull(applied.session.debtResolution!!.eventBankDebit!!.obligationId)

        val settled = engine.process(
            applied.session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_19")),
        )
        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertEquals(EventResolutionPhase.COMPLETE, settled.session.pendingEventResolution!!.phase)
        assertTrue(settled.session.pendingEventResolution!!.bankingRecorded)

        val expectedBalance = salePrice - maintenance
        assertEquals(expectedBalance, settled.session.players["USR_01"]!!.balance)

        val bankDebits = settled.session.transactions.filter {
            it.transactionType == TransactionType.BANK_DEBIT && it.eventId == "EVT_08"
        }
        assertEquals(maintenance, bankDebits.sumOf { it.amount ?: 0 })

        val propertySales = settled.session.transactions.filter {
            it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE &&
                it.propertyId == "PRP_19"
        }
        assertEquals(1, propertySales.size)
        assertNull(settled.session.properties["PRP_19"]!!.ownerPlayerId)

        val settledAgain = engine.process(
            settled.session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_19")),
        )
        assertEquals(GameOutcome.REJECTED, settledAgain.outcome)
        assertEquals(expectedBalance, settledAgain.session.players["USR_01"]!!.balance)
    }

    @Test
    fun settlementUndoRestoresBalanceOwnershipAndEventDebt() {
        val ownedIds = listOf("PRP_01", "PRP_19")
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
            mapOf("USR_01" to 0, "USR_02" to 25000),
        )
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                if (id in ownedIds) state.copy(ownerPlayerId = "USR_01") else state
            },
        )
        val beforeBalance = session.players["USR_01"]!!.balance
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        val settled = engine.process(
            applied.session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_19")),
        )
        val undone = engine.process(settled.session, GameCommand.UndoLastAction).session

        assertEquals(beforeBalance, undone.players["USR_01"]!!.balance)
        assertEquals("USR_01", undone.properties["PRP_19"]!!.ownerPlayerId)
        assertNotNull(undone.debtResolution)
        assertNotNull(undone.pendingEventResolution)
        assertNotNull(undone.pendingEventExecution)
    }

    @Test
    fun stalePendingEventExecution_doesNotDoubleChargeOnResume() {
        val amountPerProperty = 2000
        val ownedIds = listOf("PRP_01", "PRP_02", "PRP_03", "PRP_04", "PRP_19")
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
            mapOf("USR_01" to 0, "USR_02" to 25000),
        )
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                if (id in ownedIds) state.copy(ownerPlayerId = "USR_01") else state
            },
        )
        val maintenance = ownedIds.size * amountPerProperty
        val salePrice = definitions.properties["PRP_19"]!!.purchasePrice
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        val pending = applied.session.pendingEventExecution!!
        val settled = engine.process(
            applied.session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_19")),
        )
        val expected = salePrice - maintenance
        assertEquals(expected, settled.session.players["USR_01"]!!.balance)
        // Simulate stale pending not cleared (e.g. UI resume before engine cleared it)
        val stale = settled.session.copy(pendingEventExecution = pending)
        val resumed = engine.process(stale, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        assertEquals(expected, resumed.session.players["USR_01"]!!.balance)
        val bankDebits = resumed.session.transactions.filter {
            it.transactionType == TransactionType.BANK_DEBIT && it.eventId == "EVT_08"
        }
        assertEquals(maintenance, bankDebits.sumOf { it.amount ?: 0 })
    }
}
