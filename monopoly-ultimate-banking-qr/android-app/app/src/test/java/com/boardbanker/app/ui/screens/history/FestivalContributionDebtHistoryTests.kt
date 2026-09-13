package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalContributionDebtHistoryTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)

    @Test
    fun recentBankingShowsAssetSaleThenFestivalTransfers() {
        var session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03"),
        ).let { base ->
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    when (id) {
                        "USR_01" -> player.copy(balance = 4000)
                        else -> player.copy(balance = 10000)
                    }
                },
                properties = base.properties + (
                    "PRP_05" to base.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
                ),
            )
        }
        session = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01")).session
        session = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05"))).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        assertTrue(entries.any { it.title.startsWith("Property Sold") })
        val festivalEntry = entries.single { it.title == "Festival Contribution" }
        val detail = festivalEntry.detail as HistoryDetail.EventMultiPlayerTransfer
        assertEquals(2, detail.transfers.size)
        assertFalse(entries.any { it.title == "Rent payment" })
        detail.transfers.forEach { transfer ->
            assertEquals("USR_01", (transfer.from as DisplayIdentity.Player).playerId)
        }
    }

    @Test
    fun bankruptcyShowsUnableToPayEntry() {
        var session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03"),
        ).let { base ->
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    when (id) {
                        "USR_01" -> player.copy(balance = 4000)
                        else -> player.copy(balance = 10000)
                    }
                },
            )
        }
        session = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01")).session
        session = engine.process(
            session,
            GameCommand.CheckBankruptcy(
                eventResolutionId = session.pendingEventResolution!!.resolutionId,
                debtId = session.debtResolution!!.eventDebt!!.debtId,
                debtorPlayerId = session.debtResolution!!.debtorPlayerId,
            ),
        ).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val bankruptcyEntry = entries.single { it.title == "Festival Contribution — Unable to Pay" }
        val detail = bankruptcyEntry.detail as HistoryDetail.EventBankruptcy
        assertEquals("USR_01", detail.playerId)
        assertTrue(detail.summaryText.contains("bankrupt"))
        assertFalse(session.transactions.any { it.transactionType == TransactionType.EVENT_PLAYER_TRANSFER })
    }
}
