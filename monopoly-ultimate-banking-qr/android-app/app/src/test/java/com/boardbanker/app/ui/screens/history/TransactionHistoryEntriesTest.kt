package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.RentLevelChangeSnapshot
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
    fun rentPaymentUsesSingleInlineTransferDetailRow() {
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

        val entries = TransactionHistoryEntries.build(session, definitions)
        val rentEntry = entries.first { it.title == "Rent payment" }

        assertEquals("Rent payment", rentEntry.title)
        val detail = rentEntry.detail as HistoryDetail.PlayerTransfer
        assertEquals("Aditya", detail.fromPlayerName)
        assertEquals("Nishith", detail.toPlayerName)
        assertEquals(MoneyFormatter.format(expectedRent, definitions), detail.amount)
    }

    @Test
    fun rentPaymentAndLevelChangeAreSeparateCompactEntries() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session

        val entries = TransactionHistoryEntries.build(session, definitions)

        assertTrue(entries.any { it.title == "Rent payment" })
        assertTrue(entries.any { it.title == "Property rent level change" })
    }

    @Test
    fun rentLevelIncreaseShowsPropertyNumberAndLevelTransition() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
                    playerId = "USR_01",
                    propertyId = "PRP_15",
                    amount = 4,
                    stateBefore = RentLevelChangeSnapshot.stateBefore(3),
                    stateAfter = RentLevelChangeSnapshot.stateAfter(4),
                ),
            ),
        )

        val levelEntry = TransactionHistoryEntries.build(session, definitions).single()
        val detail = levelEntry.detail as HistoryDetail.RentLevelChange

        assertEquals("Property rent level change", levelEntry.title)
        assertEquals(PropertyDisplayNames.displayNameWithNumber("PRP_15", definitions), detail.propertyName)
        assertEquals(3, detail.oldLevel)
        assertEquals(4, detail.newLevel)
        assertEquals("From L3 → L4", detail.levelChangeText)
        assertFalse(detail.levelChangeText.contains("M"))
    }

    @Test
    fun rentPaymentRecordsOldAndNewRentLevelsInTransactionLog() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session

        val levelTx = session.transactions.last {
            it.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE
        }

        assertEquals(1, RentLevelChangeSnapshot.oldLevel(levelTx))
        assertEquals(2, RentLevelChangeSnapshot.newLevel(levelTx))
    }

    @Test
    fun rentLevelDecreaseDisplaysCorrectTransition() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
                    playerId = "USR_01",
                    propertyId = "PRP_15",
                    amount = 3,
                    stateBefore = RentLevelChangeSnapshot.stateBefore(4),
                    stateAfter = RentLevelChangeSnapshot.stateAfter(3),
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, definitions).single().detail
            as HistoryDetail.RentLevelChange

        assertEquals("From L4 → L3", detail.levelChangeText)
        assertEquals("[15] Leicester Square", detail.propertyName)
    }

    @Test
    fun legacyRentLevelWithoutOldLevelUsesToLevelFallback() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
                    playerId = "USR_01",
                    propertyId = "PRP_15",
                    amount = 4,
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, definitions).single().detail
            as HistoryDetail.RentLevelChange

        assertEquals(null, detail.oldLevel)
        assertEquals(4, detail.newLevel)
        assertEquals("To L4", detail.levelChangeText)
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
        val detail = entry.detail as HistoryDetail.Text
        val payout = MoneyFormatter.format(definitions.bankingValues.eventAmounts.m200, definitions)
        assertTrue(detail.value.contains("Nishith"))
        assertTrue(detail.value.contains("Aditya"))
        assertTrue(detail.value.contains(payout))
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
        assertTrue((entry.detail as HistoryDetail.Text).value.contains("Aditya"))
    }

    @Test
    fun propertyPurchaseUsesInlineTransferDetail() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session

        val purchaseEntry = TransactionHistoryEntries.build(session, definitions)
            .single { it.title == "Property purchase" }
        val detail = purchaseEntry.detail

        assertTrue(detail.toString(), detail is HistoryDetail.PlayerTransfer)
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
        val detail = undoEntry.detail
        assertTrue(detail is HistoryDetail.PlayerTransfer)
        detail as HistoryDetail.PlayerTransfer
        assertEquals("Nishith", detail.fromPlayerName)
        assertEquals("Bank", detail.toPlayerName)
        assertEquals(
            MoneyFormatter.format(definitions.properties["PRP_01"]!!.purchasePrice, definitions),
            detail.amount,
        )
    }

    @Test
    fun undoMarksOnlyTheRevertedAction() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        Thread.sleep(5)
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_03"),
        ).session
        Thread.sleep(5)
        session = AppTestSupport.engine.process(session, GameCommand.UndoLastAction).session

        val entries = TransactionHistoryEntries.build(session, definitions)
        val purchases = entries.filter { it.title == "Property purchase" }

        assertEquals("Undo", entries[0].title)
        assertTrue(purchases.any { it.undone })
        assertEquals(1, purchases.count { it.undone })
        assertEquals(1, purchases.count { !it.undone })
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
            (entries.first().detail as HistoryDetail.PlayerTransfer).amount,
        )
        assertEquals(
            MoneyFormatter.format(total - TransactionHistoryEntries.MAX_ENTRIES + 1, definitions),
            (entries.last().detail as HistoryDetail.PlayerTransfer).amount,
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
        assertTrue((entries.single().detail as HistoryDetail.Text).value.isNotBlank())
    }
}
