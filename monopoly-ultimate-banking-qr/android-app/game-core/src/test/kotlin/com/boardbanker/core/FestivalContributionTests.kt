package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.PlayerOrder
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FestivalContributionTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun festivalContribution_twoPlayers_recordsAllRecipientsAndTotal() {
        val session = indiaGame(
            players = listOf("USR_01", "USR_02"),
            balances = mapOf("USR_01" to 50000, "USR_02" to 10000),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEventTransferResult(
            result = result,
            actingPlayerId = "USR_01",
            expectedRecipients = listOf("USR_02"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun festivalContribution_threePlayers_recordsAllRecipientsAndTotal() {
        val session = indiaGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            balances = mapOf("USR_01" to 50000, "USR_02" to 10000, "USR_03" to 10000),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEventTransferResult(
            result = result,
            actingPlayerId = "USR_01",
            expectedRecipients = listOf("USR_02", "USR_03"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun festivalContribution_fourPlayers_recordsAllRecipientsAndTotal() {
        val session = indiaGame(
            players = listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            balances = mapOf(
                "USR_01" to 50000,
                "USR_02" to 10000,
                "USR_03" to 10000,
                "USR_04" to 10000,
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEventTransferResult(
            result = result,
            actingPlayerId = "USR_01",
            expectedRecipients = listOf("USR_02", "USR_03", "USR_04"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun festivalContribution_throughLuckyDraw_recordsStructuredTransfer() {
        var session = indiaGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            balances = mapOf("USR_01" to 50000, "USR_02" to 10000, "USR_03" to 10000),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val result = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_06", "USR_01"))

        assertEventTransferResult(
            result = result,
            actingPlayerId = "USR_01",
            expectedRecipients = listOf("USR_02", "USR_03"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun festivalContribution_undoRestoresBalancesAndRemovesSummary() {
        val session = indiaGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            balances = mapOf("USR_01" to 50000, "USR_02" to 10000, "USR_03" to 10000),
        )
        val beforeBalances = session.players.mapValues { it.value.balance }
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))
        val undone = engine.process(applied.session, GameCommand.UndoLastAction)

        assertEquals(beforeBalances, undone.session.players.mapValues { it.value.balance })
        assertEquals(TransactionType.UNDO, undone.session.transactions.last().transactionType)
    }

    @Test
    fun festivalContribution_serializerRoundTripPreservesSummaryMetadata() {
        val session = indiaGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            balances = mapOf("USR_01" to 50000, "USR_02" to 10000, "USR_03" to 10000),
        )
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))
        val json = serializer.serialize(applied.session)
        val restored = serializer.deserialize(json)
        val summaryTx = restored.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)!!

        assertEquals("EVT_06", metadata.eventId)
        assertEquals("Festival Contribution", metadata.eventName)
        assertEquals(2, metadata.transfers.size)
        assertEquals(10000, metadata.totalAmount)
    }

    private fun indiaGame(
        players: List<String>,
        balances: Map<String, Int>,
    ) = TestFixtures.newGameForEdition(EditionIds.INDIA, players, balances)

    private fun assertEventTransferResult(
        result: com.boardbanker.core.engine.GameResult,
        actingPlayerId: String,
        expectedRecipients: List<String>,
        amountPerRecipient: Int,
    ) {
        val session = result.session
        assertFalse(
            session.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT },
        )
        assertEquals(
            expectedRecipients.size,
            session.transactions.count { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER },
        )
        val summaryTx = session.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)
        assertNotNull(metadata)
        assertEquals("EVT_06", metadata!!.eventId)
        assertEquals("Festival Contribution", metadata.eventName)
        assertEquals(actingPlayerId, metadata.payerPlayerId)
        assertEquals(
            EventMultiPlayerTransferSnapshot.Direction.PAY_EACH_PLAYER,
            metadata.direction,
        )
        assertEquals(expectedRecipients.size * amountPerRecipient, metadata.totalAmount)
        assertEquals(
            expectedRecipients,
            metadata.transfers.map { it.toPlayerId },
        )
        assertEquals(
            expectedRecipients,
            PlayerOrder.displayOrder(session)
                .filter { it != actingPlayerId }
                .filter { session.players[it]?.active == true && session.players[it]?.bankrupt != true },
        )
        metadata.transfers.forEach { transfer ->
            assertEquals(actingPlayerId, transfer.fromPlayerId)
            assertEquals(amountPerRecipient, transfer.amount)
        }
        assertTrue(metadata.transferId.isNotBlank())
    }
}
