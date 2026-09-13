package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtPropertySettlementSnapshot
import com.boardbanker.core.model.DebtAssetSettlementMethod
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.DebtSettlementSnapshot
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.rules.BankruptcyRules
import com.boardbanker.core.rules.DebtRules
import com.boardbanker.core.rules.WinnerCalculator
import com.boardbanker.core.transaction.TransactionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtSettlementTests {
    private val engine = TestFixtures.engine
    private val definitions = TestFixtures.definitions
    private val transactionFactory = TransactionFactory()
    private val debtRules = DebtRules(
        definitions,
        transactionFactory,
        BankruptcyRules(definitions, transactionFactory, WinnerCalculator(definitions)),
    )
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun enteringPlayerDebtCreditsCreditorWithAvailableCash() {
        val session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 1000, "USR_02" to 600))
        val result = debtRules.enterDebtResolution(
            session = session,
            debtorId = "USR_02",
            creditorId = "USR_01",
            amount = 1000,
            reason = DebtReason.RENT,
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.session!!.players["USR_02"]!!.balance)
        assertEquals(1600, result.session.players["USR_01"]!!.balance)
        assertEquals(600, result.session.debtResolution!!.cashAmountUsed)
        assertEquals(400, result.session.debtResolution!!.amountRemaining)
    }

    @Test
    fun playerCreditorWithoutChangeFundsUsesBankSaleFallback() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        session = session.copy(
            players = session.players + ("USR_01" to session.players["USR_01"]!!.copy(balance = 0)),
        )

        val result = debtRules.resolveWithProperties(session, propertyIds = listOf("PRP_10"))

        assertTrue(result.isSuccess)
        assertEquals(20, result.session!!.players["USR_02"]!!.balance)
        assertEquals(160, result.session.players["USR_01"]!!.balance)
        assertNull(result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.BANK_CREDIT })
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.RENT_PAYMENT })
        val settlement = DebtSettlementSnapshot.fromTransaction(
            result.transactions.single { it.transactionType == TransactionType.RENT_DEBT_SETTLED },
        )!!
        assertEquals(DebtAssetSettlementMethod.SELL_TO_BANK, settlement.settlementMethod)
        assertTrue(settlement.propertyActions.single().soldToBank)
    }

    @Test
    fun zeroCashRentDebtSettlementCompletesWithStructuredRecord() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 1500, "USR_02" to 0))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
                    "PRP_10" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
                    else -> state
                }
            },
        )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02")
        session = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_12")).session
        assertNotNull(session.debtResolution)
        assertEquals(0, session.players["USR_02"]!!.balance)
        assertEquals(0, session.debtResolution!!.cashAmountUsed)

        val result = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10")))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertNotNull(result.session.debtResolution)
        assertEquals("USR_01", result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertTrue(result.session.debtResolution!!.amountRemaining > 0)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_DEBT_SETTLED })
    }

    @Test
    fun singlePropertySettlementTransfersOwnershipOnce() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        val ownershipBefore = session.properties["PRP_10"]!!.ownerPlayerId
        val result = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10")))
        assertEquals("USR_01", result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals(ownershipBefore, "USR_02")
        assertTrue(result.transactions.count { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE } == 1)
    }

    @Test
    fun multiplePropertySettlementTransfersEachSelectedProperty() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 300,
            debtorBalance = 0,
            ownedPropertyIds = listOf("PRP_10", "PRP_11"),
        )
        val result = engine.process(
            session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_10", "PRP_11")),
        )
        assertEquals("USR_01", result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals("USR_01", result.session.properties["PRP_11"]!!.ownerPlayerId)
        assertEquals(2, result.transactions.count { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE })
        assertNotNull(DebtSettlementSnapshot.fromTransaction(
            result.transactions.single { it.transactionType == TransactionType.RENT_DEBT_SETTLED },
        ))
    }

    @Test
    fun cashPlusPropertySettlementRecordsBothAmounts() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
            originalAmountDue = 200,
            cashAmountUsed = 40,
        )
        val result = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10")))
        val metadata = DebtSettlementSnapshot.fromTransaction(
            result.transactions.single { it.transactionType == TransactionType.RENT_DEBT_SETTLED },
        )!!
        assertEquals(200, metadata.originalAmountDue)
        assertEquals(40, metadata.cashAmountUsed)
        assertTrue(metadata.propertyValueUsed > 0)
    }

    @Test
    fun emptySelectionReturnsErrorWithoutMutatingSession() {
        val session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        val result = debtRules.resolveWithProperties(session, propertyIds = emptyList())
        assertFalse(result.isSuccess)
        assertEquals("No assets selected", result.error)
        assertNull(result.session)
    }

    @Test
    fun duplicateSelectionReturnsErrorWithoutMutatingSession() {
        val session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        val result = debtRules.resolveWithProperties(session, propertyIds = listOf("PRP_10", "PRP_10"))
        assertFalse(result.isSuccess)
        assertEquals("Duplicate property selected", result.error)
    }

    @Test
    fun propertyNoLongerOwnedByDebtorIsRejected() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_10" to session.properties["PRP_10"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        val result = debtRules.resolveWithProperties(session, propertyIds = listOf("PRP_10"))
        assertFalse(result.isSuccess)
        assertEquals("Property not owned by debtor", result.error)
    }

    @Test
    fun settlementUndoRestoresDebtBalancesAndOwnership() {
        var session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_10"))).session
        assertNotNull(settled.undoSnapshot)
        val undone = engine.process(settled, GameCommand.UndoLastAction).session
        assertEquals("USR_02", undone.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals(160, undone.debtResolution!!.amountRemaining)
        assertEquals(0, undone.players["USR_02"]!!.balance)
    }

    @Test
    fun propertySettlementSnapshotStoredOnOwnershipChange() {
        val session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        )
        val result = debtRules.resolveWithProperties(session, propertyIds = listOf("PRP_10"))
        val ownershipTx = result.transactions.single { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE }
        val snapshot = DebtPropertySettlementSnapshot.fromTransaction(ownershipTx)
        assertNotNull(snapshot)
        assertEquals("PRP_10", snapshot!!.propertyId)
        assertFalse(snapshot.soldToBank)
        assertEquals("USR_01", snapshot.destination)
    }

    @Test
    fun legacyDebtResolutionStateLoadsWithSafeDefaults() {
        val session = debtSession(
            debtorId = "USR_02",
            creditorId = "USR_01",
            amountRemaining = 160,
            debtorBalance = 0,
            ownedPropertyId = "PRP_10",
        ).copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 160,
            ),
        )
        val json = serializer.serialize(session)
        val restored = serializer.deserialize(json)
        assertEquals(160, restored.debtResolution!!.originalAmountDue)
        assertEquals(0, restored.debtResolution!!.cashAmountUsed)
    }

    private fun debtSession(
        debtorId: String,
        creditorId: String,
        amountRemaining: Int,
        debtorBalance: Int,
        ownedPropertyId: String? = null,
        ownedPropertyIds: List<String> = ownedPropertyId?.let { listOf(it) } ?: emptyList(),
        originalAmountDue: Int = amountRemaining,
        cashAmountUsed: Int = 0,
    ): com.boardbanker.core.model.GameSession {
        var session = TestFixtures.sessionWithBalances(
            mapOf(
                debtorId to debtorBalance,
                creditorId to 1500,
            ),
        )
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                if (id in ownedPropertyIds) {
                    state.copy(ownerPlayerId = debtorId, currentRentLevel = 1)
                } else {
                    state
                }
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = debtorId,
                creditorPlayerId = creditorId,
                amountRemaining = amountRemaining,
                reason = DebtReason.RENT,
                propertyId = "PRP_12",
                originalAmountDue = originalAmountDue,
                cashAmountUsed = cashAmountUsed,
            ),
        )
        return session
    }
}
