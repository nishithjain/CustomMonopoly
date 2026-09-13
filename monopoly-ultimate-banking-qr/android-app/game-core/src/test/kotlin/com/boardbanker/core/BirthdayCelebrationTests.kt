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

class BirthdayCelebrationTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun birthdayCelebration_twoPlayers_recordsAllContributorsAndTotal() {
        val session = birthdayGame(
            players = listOf("USR_01", "USR_02"),
            recipientId = "USR_02",
            balances = mapOf("USR_01" to 10000, "USR_02" to 50000),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))

        assertCollectTransferResult(
            result = result,
            recipientId = "USR_02",
            expectedContributors = listOf("USR_01"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun birthdayCelebration_threePlayers_recordsAllContributorsAndTotal() {
        val session = birthdayGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_03",
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_03"))

        assertCollectTransferResult(
            result = result,
            recipientId = "USR_03",
            expectedContributors = listOf("USR_01", "USR_02"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun birthdayCelebration_fourPlayers_recordsAllContributorsAndTotal() {
        val session = birthdayGame(
            players = listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            recipientId = "USR_04",
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04"))

        assertCollectTransferResult(
            result = result,
            recipientId = "USR_04",
            expectedContributors = listOf("USR_01", "USR_02", "USR_03"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun birthdayCelebration_throughLuckyDraw_recordsStructuredTransfer() {
        var session = birthdayGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_01",
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val result = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_07", "USR_01"))

        assertCollectTransferResult(
            result = result,
            recipientId = "USR_01",
            expectedContributors = listOf("USR_02", "USR_03"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun birthdayCelebration_undoRestoresBalancesAndRemovesSummary() {
        val session = birthdayGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_03",
        )
        val beforeBalances = session.players.mapValues { it.value.balance }
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_03"))
        val undone = engine.process(applied.session, GameCommand.UndoLastAction)

        assertEquals(beforeBalances, undone.session.players.mapValues { it.value.balance })
        assertEquals(TransactionType.UNDO, undone.session.transactions.last().transactionType)
    }

    @Test
    fun birthdayCelebration_serializerRoundTripPreservesSummaryMetadata() {
        val session = birthdayGame(
            players = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_03",
        )
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_03"))
        val json = serializer.serialize(applied.session)
        val restored = serializer.deserialize(json)
        val summaryTx = restored.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)!!

        assertEquals("EVT_07", metadata.eventId)
        assertEquals("Birthday Celebration", metadata.eventName)
        assertEquals(2, metadata.transfers.size)
        assertEquals(10000, metadata.totalAmount)
        assertEquals(
            EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER,
            metadata.direction,
        )
    }

    private fun birthdayGame(
        players: List<String>,
        recipientId: String,
        balances: Map<String, Int>? = null,
    ): com.boardbanker.core.model.GameSession {
        val defaultBalances = players.associateWith { playerId ->
            if (playerId == recipientId) 50000 else 10000
        }
        return TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            players,
            balances ?: defaultBalances,
        )
    }

    private fun assertCollectTransferResult(
        result: com.boardbanker.core.engine.GameResult,
        recipientId: String,
        expectedContributors: List<String>,
        amountPerContributor: Int,
    ) {
        val session = result.session
        assertFalse(session.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertEquals(
            expectedContributors.size,
            session.transactions.count { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER },
        )
        val summaryTx = session.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)
        assertNotNull(metadata)
        assertEquals("EVT_07", metadata!!.eventId)
        assertEquals("Birthday Celebration", metadata.eventName)
        assertEquals(recipientId, metadata.payerPlayerId)
        assertEquals(
            EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER,
            metadata.direction,
        )
        assertEquals(expectedContributors.size * amountPerContributor, metadata.totalAmount)
        assertEquals(
            expectedContributors,
            metadata.transfers.map { it.fromPlayerId },
        )
        assertEquals(
            expectedContributors,
            PlayerOrder.displayOrder(session)
                .filter { it != recipientId }
                .filter { session.players[it]?.active == true && session.players[it]?.bankrupt != true },
        )
        metadata.transfers.forEach { transfer ->
            assertEquals(recipientId, transfer.toPlayerId)
            assertEquals(amountPerContributor, transfer.amount)
        }
        assertTrue(metadata.transferId.isNotBlank())
        assertTrue(session.players.values.none { it.balance < 0 })
    }
}
