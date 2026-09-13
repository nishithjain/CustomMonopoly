package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayCelebrationHistoryTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun recentBanking_twoPlayers_showsBirthdayCelebrationWithSingleTransfer() {
        assertBirthdayCelebrationHistory(
            playerIds = listOf("USR_01", "USR_02"),
            recipientId = "USR_02",
            expectedContributorIds = listOf("USR_01"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun recentBanking_threePlayers_showsAllContributorsAndTotal() {
        assertBirthdayCelebrationHistory(
            playerIds = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_03",
            expectedContributorIds = listOf("USR_01", "USR_02"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun recentBanking_fourPlayers_showsAllContributorsAndTotal() {
        assertBirthdayCelebrationHistory(
            playerIds = listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            recipientId = "USR_04",
            expectedContributorIds = listOf("USR_01", "USR_02", "USR_03"),
            amountPerContributor = 5000,
        )
    }

    @Test
    fun recentBanking_luckyDrawPath_showsBirthdayCelebrationEntry() {
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
        session = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_07", "USR_01")).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val birthdayEntry = entries.single { it.title == "Birthday Celebration" }
        val detail = birthdayEntry.detail as HistoryDetail.EventMultiPlayerTransfer

        assertEquals(CommonUiIcon.EVENT_CARD, birthdayEntry.entryIcon)
        assertFalse(entries.any { it.title == "Rent payment" })
        assertEquals(2, detail.transfers.size)
        assertTrue(detail.summaryText.contains("received birthday contributions"))
        assertTrue(detail.totalLabel.contains("Total received by"))
    }

    @Test
    fun restoredSessionPreservesBirthdayCelebrationHistory() {
        val session = birthdaySession(
            playerIds = listOf("USR_01", "USR_02", "USR_03"),
            recipientId = "USR_03",
        )
        val restored = serializer.deserialize(serializer.serialize(session))
        val entries = TransactionHistoryEntries.build(restored, definitions)

        assertEquals(1, entries.count { it.title == "Birthday Celebration" })
        val detail = entries.single { it.title == "Birthday Celebration" }.detail
            as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(2, detail.transfers.size)
        assertTrue(detail.totalLabel.contains("Total received by"))
    }

    private fun birthdaySession(
        playerIds: List<String>,
        recipientId: String,
    ): com.boardbanker.core.model.GameSession {
        val balances = playerIds.associateWith { playerId ->
            if (playerId == recipientId) 50000 else 10000
        }
        var session = TestFixturesCompat.newGameForEdition(EditionIds.INDIA, playerIds, balances)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", recipientId)).session
        return session
    }

    private fun assertBirthdayCelebrationHistory(
        playerIds: List<String>,
        recipientId: String,
        expectedContributorIds: List<String>,
        amountPerContributor: Int,
    ) {
        val session = birthdaySession(playerIds, recipientId)
        val entries = TransactionHistoryEntries.build(session, definitions)
        val birthdayEntries = entries.filter { it.title == "Birthday Celebration" }

        assertEquals(1, birthdayEntries.size)
        assertFalse(entries.any { it.title == "Rent payment" })

        val entry = birthdayEntries.single()
        assertEquals(CommonUiIcon.EVENT_CARD, entry.entryIcon)
        val detail = entry.detail as HistoryDetail.EventMultiPlayerTransfer

        assertEquals(recipientId, detail.payerPlayerId)
        assertTrue(detail.summaryText.contains("received birthday contributions"))
        assertEquals(expectedContributorIds.size, detail.transfers.size)
        assertEquals(
            expectedContributorIds,
            detail.transfers.map { (it.from as DisplayIdentity.Player).playerId },
        )
        detail.transfers.forEach { transfer ->
            assertEquals(recipientId, (transfer.to as DisplayIdentity.Player).playerId)
            assertTrue(transfer.amount.contains(amountPerContributor.toString().first().toString()))
        }

        val expectedTotal = expectedContributorIds.size * amountPerContributor
        val summaryTx = session.transactions.single {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)!!
        assertEquals(expectedTotal, metadata.totalAmount)
        assertTrue(detail.totalLabel.contains("Total received by"))
        assertTrue(detail.totalPaid.contains(expectedTotal.toString().first().toString()))
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
