package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayCelebrationDebtHistoryTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)

    @Test
    fun recentBankingShowsAssetSaleThenBirthdayTransfers() {
        var session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        ).let { base ->
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    when (id) {
                        "USR_02" -> player.copy(balance = 2000)
                        "USR_04" -> player.copy(balance = 50000)
                        else -> player.copy(balance = 10000)
                    }
                },
                properties = base.properties + (
                    "PRP_05" to base.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_02")
                ),
            )
        }
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        session = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05"))).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        assertTrue(entries.any { it.title.startsWith("Property Sold") })
        assertEquals(1, entries.count { it.title == "Birthday Celebration" })
        val birthdayEntry = entries.single { it.title == "Birthday Celebration" }
        val detail = birthdayEntry.detail as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(3, detail.transfers.size)
        assertFalse(entries.any { it.title == "Rent payment" })
        assertTrue(detail.summaryText.contains("received birthday contributions"))
        detail.transfers.forEach { transfer ->
            assertEquals("USR_04", (transfer.to as DisplayIdentity.Player).playerId)
        }
    }

    @Test
    fun contributorBankruptcyShowsUnableToPayEntry() {
        var session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        ).let { base ->
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    when (id) {
                        "USR_01" -> player.copy(balance = 2000)
                        "USR_04" -> player.copy(balance = 50000)
                        else -> player.copy(balance = 10000)
                    }
                },
            )
        }
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_04")).session
        session = engine.process(
            session,
            GameCommand.CheckBankruptcy(
                eventResolutionId = session.pendingEventResolution!!.resolutionId,
                debtId = session.debtResolution!!.eventContributorDebt!!.debtId,
                debtorPlayerId = session.debtResolution!!.debtorPlayerId,
            ),
        ).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val bankruptcyEntry = entries.single { it.title == "Birthday Celebration — Unable to Pay" }
        val detail = bankruptcyEntry.detail as HistoryDetail.EventBankruptcy
        assertEquals("USR_01", detail.playerId)
        assertTrue(detail.summaryText.contains("bankrupt"))
        val birthdayEntry = entries.single { it.title == "Birthday Celebration" }
        val transferDetail = birthdayEntry.detail as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(3, transferDetail.transfers.size)
        assertEquals(
            MoneyFormatter.format(2000, definitions),
            transferDetail.transfers.first {
                (it.from as DisplayIdentity.Player).playerId == "USR_01"
            }.amount,
        )
    }
}
