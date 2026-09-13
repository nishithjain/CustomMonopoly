package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
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

class BirthdayCelebrationDebtTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun insufficientCashOpensStructuredContributorDebtWithoutPartialPayments() {
        val session = birthdaySession(
            balances = mapOf(
                "USR_01" to 10000,
                "USR_02" to 2000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        val debt = result.session.debtResolution!!
        assertEquals(DebtReason.EVENT_CONTRIBUTOR, debt.reason)
        assertNotNull(debt.eventContributorDebt)
        assertEquals("EVT_07", debt.eventContributorDebt!!.eventId)
        assertEquals("USR_02", debt.eventContributorDebt!!.contributorPlayerId)
        assertEquals("USR_04", debt.eventContributorDebt!!.recipientPlayerId)
        assertEquals(5000, debt.eventContributorDebt!!.contributionAmount)
        assertEquals(2000, debt.eventContributorDebt!!.cashAvailable)
        assertEquals(3000, debt.eventContributorDebt!!.shortfall)
        assertEquals(1, result.session.transactions.count { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
        assertEquals("USR_01", result.session.transactions
            .single { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER }.fromEntity)
        assertFalse(result.session.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertNotNull(result.session.pendingEventMultiContributorSettlement)
        assertNotNull(result.session.pendingEventExecution)
        assertTrue(result.session.players.values.none { it.balance < 0 })
    }

    @Test
    fun assetSaleCoversShortfallAndPaysRecipientWithoutTransferringProperty() {
        var session = birthdaySession(
            balances = mapOf(
                "USR_01" to 10000,
                "USR_02" to 2000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_02")
            ),
        )
        val recipientBefore = session.players["USR_04"]!!.balance
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))

        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertNull(settled.session.properties["PRP_05"]!!.ownerPlayerId)
        assertEquals(recipientBefore + 15000, settled.session.players["USR_04"]!!.balance)
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
    fun multipleContributorsWithInsufficientCashAreResolvedSequentially() {
        var session = birthdaySession(
            balances = mapOf(
                "USR_01" to 2000,
                "USR_02" to 2000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        session = session.copy(
            properties = session.properties + mapOf(
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01"),
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_02"),
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        assertEquals("USR_01", session.debtResolution!!.debtorPlayerId)

        session = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_01"))).session
        assertEquals("USR_02", session.debtResolution!!.debtorPlayerId)

        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))
        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(settled.session.debtResolution)
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(
            settled.session.transactions.single {
                it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
            },
        )!!
        assertEquals(listOf("USR_01", "USR_02", "USR_03"), metadata.transfers.map { it.fromPlayerId })
        assertEquals(15000, metadata.totalAmount)
    }

    @Test
    fun pendingDebtDoesNotTransferPropertyToRecipient() {
        var session = birthdaySession(
            balances = mapOf(
                "USR_01" to 10000,
                "USR_02" to 2000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_05" to session.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_02")
            ),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session

        assertEquals("USR_02", session.properties["PRP_05"]!!.ownerPlayerId)
        assertFalse(session.transactions.any {
            it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_02"
        })
    }

    @Test
    fun contributorBankruptcySkipsObligationAndContinuesSettlement() {
        val session = birthdaySession(
            balances = mapOf(
                "USR_01" to 2000,
                "USR_02" to 10000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        val recipientBefore = session.players["USR_04"]!!.balance
        val current = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        val bankrupt = engine.process(
            current,
            GameCommand.CheckBankruptcy(
                eventResolutionId = current.pendingEventResolution!!.resolutionId,
                debtId = current.debtResolution!!.eventContributorDebt!!.debtId,
                debtorPlayerId = current.debtResolution!!.debtorPlayerId,
            ),
        )

        assertEquals(GameOutcome.SUCCESS, bankrupt.outcome)
        assertTrue(bankrupt.session.players["USR_01"]!!.bankrupt)
        assertEquals(GameStatus.ACTIVE, bankrupt.session.status)
        assertNull(bankrupt.session.debtResolution)
        assertNull(bankrupt.session.pendingEventExecution)
        assertEquals(recipientBefore + 12000, bankrupt.session.players["USR_04"]!!.balance)
        assertEquals(0, bankrupt.session.players["USR_01"]!!.balance)
        assertEquals(
            1,
            bankrupt.session.transactions.count {
                it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_01"
            },
        )
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(
            bankrupt.session.transactions.single {
                it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
            },
        )!!
        assertEquals(listOf("USR_01", "USR_02", "USR_03"), metadata.transfers.map { it.fromPlayerId })
        assertEquals(12000, metadata.totalAmount)
        assertEquals(2000, metadata.transfers.first { it.fromPlayerId == "USR_01" }.amount)
    }

    @Test
    fun contributorBankruptcyCommandIsBoundToDebtAndIsIdempotent() {
        val applied = engine.process(
            birthdaySession(
                balances = mapOf(
                    "USR_01" to 2000,
                    "USR_02" to 10000,
                    "USR_03" to 10000,
                    "USR_04" to 50000,
                ),
            ),
            GameCommand.ApplyEvent("EVT_07", "USR_04"),
        ).session
        val debt = applied.debtResolution!!
        val contributorDebt = debt.eventContributorDebt!!
        val command = GameCommand.CheckBankruptcy(
            eventResolutionId = applied.pendingEventResolution!!.resolutionId,
            debtId = contributorDebt.debtId,
            debtorPlayerId = debt.debtorPlayerId,
        )
        val first = engine.process(applied, command)
        val repeated = engine.process(first.session, command)

        assertEquals(GameOutcome.SUCCESS, first.outcome)
        assertEquals(GameOutcome.REJECTED, repeated.outcome)
        assertEquals(1, first.session.transactions.count { it.transactionType == TransactionType.BANKRUPTCY })
        assertEquals(1, first.session.transactions.count {
            it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_01"
        })
    }

    @Test
    fun contributorBankruptcyWithRecipientAsCurrentPlayerPaysCashOnceAndContinues() {
        var session = birthdaySession(
            balances = mapOf(
                "USR_01" to 4000,
                "USR_02" to 10000,
                "USR_03" to 10000,
                "USR_04" to 50000,
            ),
        )
        session = session.copy(turnState = session.turnState!!.copy(activePlayerId = "USR_04"))
        val recipientBefore = session.players["USR_04"]!!.balance
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        val debt = session.debtResolution!!
        val command = GameCommand.CheckBankruptcy(
            eventResolutionId = session.pendingEventResolution!!.resolutionId,
            debtId = debt.eventContributorDebt!!.debtId,
            debtorPlayerId = "USR_01",
        )

        val first = engine.process(session, command)
        val repeated = engine.process(first.session, command)

        assertEquals(GameOutcome.SUCCESS, first.outcome)
        assertEquals("USR_04", first.session.turnState!!.activePlayerId)
        assertEquals(0, first.session.players["USR_01"]!!.balance)
        assertTrue(first.session.players["USR_01"]!!.bankrupt)
        assertFalse(first.session.players["USR_01"]!!.active)
        assertEquals(recipientBefore + 14000, first.session.players["USR_04"]!!.balance)
        assertNull(first.session.debtResolution)
        assertTrue(first.session.pendingEventResolution!!.isComplete())
        assertEquals(1, first.session.transactions.count {
            it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_01"
        })
        assertEquals(GameOutcome.REJECTED, repeated.outcome)
        assertEquals(1, repeated.session.transactions.count {
            it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_01"
        })
    }

    @Test
    fun emptyAssetSelectionDoesNotMutateSession() {
        val session = engine.process(
            birthdaySession(
                balances = mapOf(
                    "USR_01" to 10000,
                    "USR_02" to 2000,
                    "USR_03" to 10000,
                    "USR_04" to 50000,
                ),
            ),
            GameCommand.ApplyEvent("EVT_07", "USR_04"),
        ).session
        val rejected = engine.process(session, GameCommand.ResolveDebtWithProperties(emptyList()))

        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertNotNull(rejected.session.debtResolution)
        assertEquals(2000, rejected.session.players["USR_02"]!!.balance)
        assertFalse(rejected.session.transactions.any {
            it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER && it.fromEntity == "USR_02"
        })
    }

    @Test
    fun contributorDebtSnapshotRoundTripsThroughSerializer() {
        val session = engine.process(
            birthdaySession(
                balances = mapOf(
                    "USR_01" to 10000,
                    "USR_02" to 2000,
                    "USR_03" to 10000,
                    "USR_04" to 50000,
                ),
            ),
            GameCommand.ApplyEvent("EVT_07", "USR_04"),
        ).session
        val restored = serializer.deserialize(serializer.serialize(session))
        val contributorDebt = restored.debtResolution!!.eventContributorDebt!!
        assertEquals("Birthday Celebration", contributorDebt.eventName)
        assertEquals("USR_04", contributorDebt.recipientPlayerId)
        assertNotNull(restored.pendingEventMultiContributorSettlement)
    }

    @Test
    fun stalePendingEventExecution_doesNotDuplicateSummaryOrTransfers() {
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03"),
            mapOf("USR_01" to 10000, "USR_02" to 0, "USR_03" to 50000),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_19" to session.properties["PRP_19"]!!.copy(ownerPlayerId = "USR_02")
            ),
        )
        val recipientBefore = session.players["USR_03"]!!.balance
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_03"))
        val pending = applied.session.pendingEventExecution!!
        val settled = engine.process(
            applied.session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_19")),
        )
        assertEquals(recipientBefore + 10000, settled.session.players["USR_03"]!!.balance)
        assertEquals(
            1,
            settled.session.transactions.count { it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER },
        )
        val stale = settled.session.copy(pendingEventExecution = pending)
        val resumed = engine.process(stale, GameCommand.ApplyEvent("EVT_07", "USR_03"))
        assertEquals(recipientBefore + 10000, resumed.session.players["USR_03"]!!.balance)
        assertEquals(
            1,
            resumed.session.transactions.count { it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER },
        )
    }

    private fun birthdaySession(
        balances: Map<String, Int>,
    ): com.boardbanker.core.model.GameSession =
        TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            balances,
        )
}
