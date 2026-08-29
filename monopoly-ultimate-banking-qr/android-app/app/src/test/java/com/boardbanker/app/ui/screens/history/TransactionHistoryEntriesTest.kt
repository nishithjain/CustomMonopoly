package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionHistoryEntriesTest {

    private val definitions = AppTestSupport.definitions

    @Test
    fun rentEntryShowsPayerOwnerPropertyAndMoney() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        val rentLevel = session.properties["PRP_01"]!!.currentRentLevel
        val expectedRent = definitions.properties["PRP_01"]!!
            .rentLevels.first { it.level == rentLevel }.amount
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions).first()

        assertEquals("Rent payment", entry.title)
        assertEquals("Old Kent Road", entry.propertyName)
        val rentLine = entry.lines.first { it.label == "Rent payment" }
        assertEquals("Aditya", rentLine.fromPlayerName)
        assertEquals("Nishith", rentLine.toPlayerName)
        assertEquals(MoneyFormatter.format(expectedRent, definitions), rentLine.detail)
    }

    @Test
    fun rentLevelChangeIsNotShownAsMoney() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions).first()
        val levelLine = entry.lines.first { it.label == "Rent level change" }

        val newLevel = session.properties["PRP_01"]!!.currentRentLevel
        assertEquals("Rent level $newLevel", levelLine.detail)
        assertFalse(levelLine.detail!!.contains(definitions.bankingValues.currency.symbol))
    }

    @Test
    fun eventEntryNamesTheCardAndGroupsItsPayouts() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_11",
                actingPlayerId = "USR_01",
                targetPlayerId = "USR_02",
            ),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions).first()

        assertEquals("Event: Love Is In The Air", entry.title)
        assertNotNull(entry.subtitle)
        val payout = MoneyFormatter.format(definitions.bankingValues.eventAmounts.m200, definitions)
        val payoutLines = entry.lines.filter { it.detail == payout }
        assertEquals(2, payoutLines.size)
        assertEquals(listOf("Nishith", "Aditya"), payoutLines.map { it.toPlayerName })
        assertTrue(entry.lines.none { it.label == "Event" })
    }

    @Test
    fun singleEventWithoutMoneyStillNamesTheCard() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_14",
                actingPlayerId = "USR_01",
                targetPlayerId = "USR_02",
            ),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions).first()

        assertEquals("Event: Pick Your Own", entry.title)
        assertTrue(entry.lines.any { it.label == "Jail" && it.playerName == "Aditya" })
    }

    @Test
    fun undoEntryShowsTheActionItReverted() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        session = AppTestSupport.engine.process(session, GameCommand.UndoLastAction).session

        val undoEntry = TransactionHistoryEntries.build(session, definitions).first()

        assertEquals("Undo", undoEntry.title)
        assertEquals("Reverted: Property purchase", undoEntry.subtitle)
        assertEquals("Old Kent Road", undoEntry.propertyName)
        val purchaseLine = undoEntry.lines.single { it.label == "Property purchase" }
        assertEquals("Nishith", purchaseLine.fromPlayerName)
        assertEquals("Bank", purchaseLine.toPlayerName)
        assertEquals(
            MoneyFormatter.format(definitions.properties["PRP_01"]!!.purchasePrice, definitions),
            purchaseLine.detail,
        )
    }

    @Test
    fun undoMarksOnlyTheRevertedAction() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        // History groups transactions that share a millisecond timestamp.
        Thread.sleep(5)
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_03"),
        ).session
        Thread.sleep(5)
        session = AppTestSupport.engine.process(session, GameCommand.UndoLastAction).session

        val angel = definitions.properties["PRP_03"]!!.name
        val kent = definitions.properties["PRP_01"]!!.name
        val entries = TransactionHistoryEntries.build(session, definitions)

        assertEquals("Undo", entries[0].title)
        assertEquals(angel, entries[0].propertyName)
        assertTrue(entries[1].undone)
        assertEquals(angel, entries[1].propertyName)
        assertFalse(entries.single { it.propertyName == kent }.undone)
        assertFalse(entries[0].undone)
    }

    @Test
    fun entriesAreCappedAndNewestFirst() {
        val base = AppTestSupport.newGame()
        val total = TransactionHistoryEntries.MAX_ENTRIES + 5
        val synthetic = (1..total).map { index ->
            Transaction(
                transactionId = "${base.gameId}_TX_$index",
                gameId = base.gameId,
                timestamp = 1_000L * index,
                transactionType = TransactionType.BANK_CREDIT,
                fromEntity = EntityRef.BANK,
                toEntity = "USR_01",
                playerId = "USR_01",
                amount = index,
            )
        }

        val entries = TransactionHistoryEntries.build(base.copy(transactions = synthetic), definitions)

        assertEquals(TransactionHistoryEntries.MAX_ENTRIES, entries.size)
        assertTrue(entries.all { it.title == "Bank payout" })
        assertEquals(
            MoneyFormatter.format(total, definitions),
            entries.first().lines.single().detail,
        )
        assertEquals(
            MoneyFormatter.format(total - TransactionHistoryEntries.MAX_ENTRIES + 1, definitions),
            entries.last().lines.single().detail,
        )
    }

    @Test
    fun transactionsFromOneActionShareAnEntry() {
        val base = AppTestSupport.newGame()
        val shared = listOf(
            Transaction(
                transactionId = "${base.gameId}_TX_1",
                gameId = base.gameId,
                timestamp = 5_000L,
                transactionType = TransactionType.BANK_DEBIT,
                fromEntity = "USR_01",
                toEntity = EntityRef.BANK,
                playerId = "USR_01",
                amount = 50,
            ),
            Transaction(
                transactionId = "${base.gameId}_TX_2",
                gameId = base.gameId,
                timestamp = 5_000L,
                transactionType = TransactionType.JAIL_STATUS_CHANGE,
                playerId = "USR_01",
            ),
        )

        val entries = TransactionHistoryEntries.build(base.copy(transactions = shared), definitions)

        assertEquals(1, entries.size)
        assertEquals("Jail", entries.single().title)
        assertEquals(2, entries.single().lines.size)
    }
}
