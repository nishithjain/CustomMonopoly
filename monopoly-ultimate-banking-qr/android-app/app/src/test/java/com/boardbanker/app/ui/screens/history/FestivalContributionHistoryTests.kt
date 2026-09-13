package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalContributionHistoryTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun recentBanking_twoPlayers_showsFestivalContributionWithSingleTransfer() {
        assertFestivalContributionHistory(
            playerIds = listOf("USR_01", "USR_02"),
            actingPlayerId = "USR_01",
            expectedRecipientIds = listOf("USR_02"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun recentBanking_threePlayers_showsAllRecipientsAndTotal() {
        assertFestivalContributionHistory(
            playerIds = listOf("USR_01", "USR_02", "USR_03"),
            actingPlayerId = "USR_01",
            expectedRecipientIds = listOf("USR_02", "USR_03"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun recentBanking_fourPlayers_showsAllRecipientsAndTotal() {
        assertFestivalContributionHistory(
            playerIds = listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            actingPlayerId = "USR_01",
            expectedRecipientIds = listOf("USR_02", "USR_03", "USR_04"),
            amountPerRecipient = 5000,
        )
    }

    @Test
    fun recentBanking_luckyDrawPath_showsFestivalContributionEntry() {
        var session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03"),
        ).let { base ->
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    when (id) {
                        "USR_01" -> player.copy(balance = 50000)
                        else -> player.copy(balance = 10000)
                    }
                },
            )
        }
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        session = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_06", "USR_01")).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val festivalEntry = entries.single { it.title == "Festival Contribution" }
        val detail = festivalEntry.detail as HistoryDetail.EventMultiPlayerTransfer

        assertEquals(CommonUiIcon.EVENT_CARD, festivalEntry.entryIcon)
        assertFalse(entries.any { it.title == "Rent payment" })
        assertEquals(2, detail.transfers.size)
        assertEquals(
            listOf("USR_02", "USR_03"),
            detail.transfers.map { (it.to as DisplayIdentity.Player).playerId },
        )
    }

    @Test
    fun repeatedHistoryBuildDoesNotDuplicateFestivalEntry() {
        val session = festivalSession(
            playerIds = listOf("USR_01", "USR_02", "USR_03"),
            actingPlayerId = "USR_01",
        )
        val first = TransactionHistoryEntries.build(session, definitions)
        val second = TransactionHistoryEntries.build(session, definitions)

        assertEquals(first, second)
        assertEquals(1, first.count { it.title == "Festival Contribution" })
        assertEquals(
            1,
            session.transactions.count { it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER },
        )
    }

    @Test
    fun legacyRentPaymentGroupStillRendersWithoutCrashing() {
        val session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03"),
        )
        val timestamp = 1_700_000_000_000L
        val legacyTransactions = listOf("USR_02", "USR_03").mapIndexed { index, recipientId ->
            com.boardbanker.core.model.Transaction(
                transactionId = "legacy_evt06_$index",
                gameId = session.gameId,
                transactionType = TransactionType.RENT_PAYMENT,
                timestamp = timestamp,
                fromEntity = "USR_01",
                toEntity = recipientId,
                playerId = "USR_01",
                eventId = "EVT_06",
                amount = 5000,
            )
        }
        val legacySession = session.copy(transactions = legacyTransactions)
        val entries = TransactionHistoryEntries.build(legacySession, definitions)

        val festivalEntry = entries.single { it.title == "Festival Contribution" }
        val detail = festivalEntry.detail as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(2, detail.transfers.size)
        assertFalse(entries.any { it.title == "Rent payment" })
    }

    @Test
    fun restoredSessionPreservesFestivalContributionHistory() {
        val session = festivalSession(
            playerIds = listOf("USR_01", "USR_02", "USR_03"),
            actingPlayerId = "USR_01",
        )
        val restored = serializer.deserialize(serializer.serialize(session))
        val entries = TransactionHistoryEntries.build(restored, definitions)

        assertEquals(1, entries.count { it.title == "Festival Contribution" })
        val detail = entries.single { it.title == "Festival Contribution" }.detail
            as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(2, detail.transfers.size)
        assertTrue(detail.totalPaid.contains("10"))
    }

    private fun festivalSession(
        playerIds: List<String>,
        actingPlayerId: String,
    ): com.boardbanker.core.model.GameSession {
        val balances = playerIds.associateWith { playerId ->
            if (playerId == actingPlayerId) 50000 else 10000
        }
        var session = TestFixturesCompat.newGameForEdition(EditionIds.INDIA, playerIds, balances)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_06", actingPlayerId)).session
        return session
    }

    private fun assertFestivalContributionHistory(
        playerIds: List<String>,
        actingPlayerId: String,
        expectedRecipientIds: List<String>,
        amountPerRecipient: Int,
    ) {
        val session = festivalSession(playerIds, actingPlayerId)
        val entries = TransactionHistoryEntries.build(session, definitions)
        val festivalEntries = entries.filter { it.title == "Festival Contribution" }

        assertEquals(1, festivalEntries.size)
        assertFalse(entries.any { it.title == "Rent payment" })

        val entry = festivalEntries.single()
        assertEquals(CommonUiIcon.EVENT_CARD, entry.entryIcon)
        val detail = entry.detail as HistoryDetail.EventMultiPlayerTransfer

        assertEquals(actingPlayerId, detail.payerPlayerId)
        assertTrue(detail.summaryText.contains("contributed to all other players"))
        assertEquals(expectedRecipientIds.size, detail.transfers.size)
        assertEquals(
            expectedRecipientIds,
            detail.transfers.map { (it.to as DisplayIdentity.Player).playerId },
        )
        detail.transfers.forEach { transfer ->
            assertEquals(actingPlayerId, (transfer.from as DisplayIdentity.Player).playerId)
            assertTrue(transfer.amount.contains(amountPerRecipient.toString().first().toString()))
        }

        val expectedTotal = expectedRecipientIds.size * amountPerRecipient
        val summaryTx = session.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)!!
        assertEquals(expectedTotal, metadata.totalAmount)
        assertTrue(detail.totalPaid.contains(expectedTotal.toString().first().toString()))
        assertEquals(expectedRecipientIds.size, metadata.transfers.size)
    }

    private object TestFixturesCompat {
        fun newGameForEdition(
            editionId: String,
            playerIds: List<String>,
            balances: Map<String, Int>,
        ): com.boardbanker.core.model.GameSession {
            val base = AppTestSupport.newGameForEdition(editionId, playerIds)
            return base.copy(
                players = base.players.mapValues { (id, player) ->
                    balances[id]?.let { player.copy(balance = it) } ?: player
                },
            )
        }
    }
}
