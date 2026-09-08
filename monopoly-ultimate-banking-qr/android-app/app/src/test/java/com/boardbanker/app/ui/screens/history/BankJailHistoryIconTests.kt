package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.JailStatusSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankJailHistoryIconTests {
    private val definitions = AppTestSupport.definitions

    @Test
    fun propertyPurchaseShowsPlayerToBankTransfer() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session

        val detail = TransactionHistoryEntries.build(session, definitions)
            .single { it.title.startsWith("Property Purchase →") }
            .detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_01", (detail.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Bank, detail.to)
    }

    @Test
    fun bankCreditShowsBankToPlayerTransfer() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.BANK_CREDIT,
                    fromEntity = EntityRef.BANK,
                    toEntity = "USR_02",
                    playerId = "USR_02",
                    amount = 2_000,
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, definitions).single()
            .detail as HistoryDetail.PlayerTransfer

        assertEquals(DisplayIdentity.Bank, detail.from)
        assertEquals("USR_02", (detail.to as DisplayIdentity.Player).playerId)
        assertEquals("Aditya", (detail.to as DisplayIdentity.Player).playerName)
    }

    @Test
    fun rentPaymentShowsBothPlayerIcons() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session

        val detail = TransactionHistoryEntries.build(session, definitions)
            .first { it.title == "Rent payment" }
            .detail as HistoryDetail.PlayerTransfer

        assertTrue(detail.from is DisplayIdentity.Player)
        assertTrue(detail.to is DisplayIdentity.Player)
        assertEquals("USR_02", (detail.from as DisplayIdentity.Player).playerId)
        assertEquals("USR_01", (detail.to as DisplayIdentity.Player).playerId)
    }

    @Test
    fun goToJailShowsPlayerToJailTransfer() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.JAIL_STATUS_CHANGE,
                    playerId = "USR_02",
                    stateBefore = JailStatusSnapshot.stateBefore(false),
                    stateAfter = JailStatusSnapshot.stateAfter(true),
                ),
            ),
        )

        val entry = TransactionHistoryEntries.build(session, definitions).single()
        val detail = entry.detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (detail.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Jail, detail.to)
        assertEquals("Sent to Jail", entry.subtitle)
    }

    @Test
    fun jailPassReleaseShowsJailToPlayerWithoutBankPayment() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.JAIL_PASS_USED,
                    playerId = "USR_02",
                    eventId = "EVT_11",
                ),
                Transaction(
                    transactionId = "${base.gameId}_TX_2",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.JAIL_STATUS_CHANGE,
                    playerId = "USR_02",
                    stateBefore = JailStatusSnapshot.stateBefore(true),
                    stateAfter = JailStatusSnapshot.stateAfter(false),
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, definitions).single()
            .detail as HistoryDetail.PlayerTransfer

        assertEquals(DisplayIdentity.Jail, detail.from)
        assertEquals("USR_02", (detail.to as DisplayIdentity.Player).playerId)
        assertEquals("", detail.amount)
    }

    @Test
    fun helicopterPlayerIconResolvedInTransfer() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.BANK_DEBIT,
                    fromEntity = "USR_02",
                    toEntity = EntityRef.BANK,
                    playerId = "USR_02",
                    amount = 100,
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, definitions).single()
            .detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (detail.from as DisplayIdentity.Player).playerId)
        assertEquals("Aditya", (detail.from as DisplayIdentity.Player).playerName)
    }

    @Test
    fun legacyJailStatusWithoutSnapshotsDoesNotCrash() {
        val base = AppTestSupport.newGame()
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.JAIL_STATUS_CHANGE,
                    playerId = "USR_01",
                ),
            ),
        )

        val entries = TransactionHistoryEntries.build(session, definitions)

        assertEquals(1, entries.size)
        assertTrue(entries.single().detail is HistoryDetail.PlayerMention || entries.single().detail is HistoryDetail.Text)
    }

    @Test
    fun indiaEditionUsesSharedBankIconMapping() {
        val indiaDefinitions = AppTestSupport.editionRepository.load(
            com.boardbanker.core.model.EditionIds.INDIA,
        )
        val base = AppTestSupport.newGameForEdition(
            editionId = com.boardbanker.core.model.EditionIds.INDIA,
            playerIds = listOf("USR_01", "USR_02"),
        )
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.BANK_CREDIT,
                    fromEntity = EntityRef.BANK,
                    toEntity = "USR_01",
                    playerId = "USR_01",
                    amount = indiaDefinitions.bankingValues.goSalary,
                ),
            ),
        )

        val detail = TransactionHistoryEntries.build(session, indiaDefinitions).single()
            .detail as HistoryDetail.PlayerTransfer

        assertEquals(DisplayIdentity.Bank, detail.from)
        assertEquals(
            MoneyFormatter.format(indiaDefinitions.bankingValues.goSalary, indiaDefinitions),
            detail.amount,
        )
    }

    @Test
    fun displayIdentityLabelsRemainAccessible() {
        assertEquals("Bank", DisplayIdentity.Bank.label)
        assertEquals("Bank", DisplayIdentity.Bank.contentDescription)
        assertEquals("Jail", DisplayIdentity.Jail.label)
        assertEquals("Jail", DisplayIdentity.Jail.contentDescription)
        assertEquals(
            "Helicopter, Player Aditya",
            DisplayIdentity.Player("USR_02", "Aditya").contentDescription,
        )
    }
}
