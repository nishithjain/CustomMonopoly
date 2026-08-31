package com.boardbanker.app.presentation

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.banking.UndoEligibility
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.CurrencyDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormattingPresentationTest {
    private val ukDefinitions = AppTestSupport.definitions
    private val ukEngine = AppTestSupport.engine
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)

    @Test
    fun ukPurchaseResultUsesMoneyFormatter() {
        val mapper = GameplayResultMapper(ukDefinitions)
        val session = AppTestSupport.newGame()
        val before = session.players["USR_01"]!!.balance
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val property = ukDefinitions.properties["PRP_01"]!!
        val balanceAfter = result.session.players["USR_01"]!!.balance
        val rentAmount = property.rentLevels.first().amount

        val ui = mapper.mapPurchaseResult(result, "USR_01", "PRP_01", before)

        assertTrue(ui.primaryMessage.contains("Paid: ${MoneyFormatter.format(property.purchasePrice, ukDefinitions)}"))
        assertTrue(ui.primaryMessage.contains("Balance: ${MoneyFormatter.format(balanceAfter, ukDefinitions)}"))
        assertTrue(ui.primaryMessage.contains("Rent: Level 1 — ${MoneyFormatter.format(rentAmount, ukDefinitions)}"))
    }

    @Test
    fun indiaPurchaseResultUsesMoneyFormatter() {
        val mapper = GameplayResultMapper(indiaDefinitions)
        val session = newGame(indiaDefinitions, indiaEngine)
        val before = session.players["USR_01"]!!.balance
        val result = indiaEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val property = indiaDefinitions.properties["PRP_01"]!!
        val balanceAfter = result.session.players["USR_01"]!!.balance

        val ui = mapper.mapPurchaseResult(result, "USR_01", "PRP_01", before)

        assertTrue(ui.primaryMessage.contains("Paid: ${MoneyFormatter.format(property.purchasePrice, indiaDefinitions)}"))
        assertTrue(ui.primaryMessage.contains("Balance: ${MoneyFormatter.format(balanceAfter, indiaDefinitions)}"))
        assertFalse(ui.primaryMessage.contains("Paid: M"))
    }

    @Test
    fun customEditionUsesFormatterWithoutKotlinMapping() {
        val customDefinitions = customDefinitions(symbol = "¤")
        val mapper = GameplayResultMapper(customDefinitions)
        val session = AppTestSupport.newGame().copy(
            players = AppTestSupport.newGame().players.mapValues { (_, player) ->
                player.copy(balance = 123)
            },
        )

        val ui = mapper.mapPlayerInfo("USR_01", session)

        assertTrue(ui.primaryMessage.contains("Balance: ¤123"))
        assertFalse(ui.primaryMessage.contains("Balance: M123"))
    }

    @Test
    fun ukRentPaymentHeadlineAndNewRentUseMoneyFormatter() {
        val mapper = GameplayResultMapper(ukDefinitions)
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = ukEngine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"))
        val rentTx = result.transactions.first { it.transactionType == TransactionType.RENT_PAYMENT }
        val rentAfter = ukDefinitions.properties["PRP_01"]!!.rentLevels
            .first { it.level == result.session.properties["PRP_01"]!!.currentRentLevel }
            .amount

        val ui = mapper.mapPropertyLandingResult(result, "USR_02", "PRP_01", before)

        assertTrue(ui.primaryMessage.startsWith(MoneyFormatter.format(rentTx.amount!!, ukDefinitions)))
        assertTrue(ui.primaryMessage.contains("New Rent: ${MoneyFormatter.format(rentAfter, ukDefinitions)}"))
    }

    @Test
    fun ukPlayerInfoBalanceUsesMoneyFormatter() {
        val mapper = GameplayResultMapper(ukDefinitions)
        val session = AppTestSupport.newGame()

        val ui = mapper.mapPlayerInfo("USR_01", session)

        assertTrue(
            ui.primaryMessage.contains(
                "Balance: ${MoneyFormatter.format(session.players["USR_01"]!!.balance, ukDefinitions)}",
            ),
        )
    }

    @Test
    fun lastTransactionSummaryUsesMoneyFormatter() {
        val mapper = GameplayResultMapper(ukDefinitions)
        val session = AppTestSupport.newGame()
        val before = session.players["USR_01"]!!.balance
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))

        val ui = mapper.mapPurchaseResult(result, "USR_01", "PRP_01", before)

        assertEquals(
            MoneyFormatter.format(ukDefinitions.properties["PRP_01"]!!.purchasePrice, ukDefinitions),
            ui.lastTransactionSummary?.substringAfterLast('\n')?.trim(),
        )
    }

    @Test
    fun undoRentPaymentUsesUkFormatter() {
        val session = sessionWithLastTransaction(
            definitions = ukDefinitions,
            transactionType = TransactionType.RENT_PAYMENT,
            amount = 70,
            fromEntity = "USR_02",
            toEntity = "USR_01",
        )

        val description = UndoEligibility(ukDefinitions).undoDescription(session)

        assertNotNull(description)
        assertTrue(description!!.contains(MoneyFormatter.format(70, ukDefinitions)))
    }

    @Test
    fun undoGoSalaryUsesUkFormatter() {
        val session = ukEngine.process(AppTestSupport.newGame(), GameCommand.PayGoSalary("USR_01")).session
        val goSalary = ukDefinitions.bankingValues.goSalary

        val description = UndoEligibility(ukDefinitions).undoDescription(session)

        assertNotNull(description)
        assertTrue(description!!.contains(MoneyFormatter.format(goSalary, ukDefinitions)))
    }

    @Test
    fun undoLocationFeeUsesUkFormatter() {
        val session = ukEngine.process(
            AppTestSupport.newGame(),
            GameCommand.PayLocationFee("USR_01", "PRP_10"),
        ).session
        val locationFee = ukDefinitions.bankingValues.locationFee

        val description = UndoEligibility(ukDefinitions).undoDescription(session)

        assertNotNull(description)
        assertTrue(description!!.contains(MoneyFormatter.format(locationFee, ukDefinitions)))
    }

    @Test
    fun indiaUndoRentPaymentUsesIndiaFormatter() {
        val session = sessionWithLastTransaction(
            definitions = indiaDefinitions,
            transactionType = TransactionType.RENT_PAYMENT,
            amount = 7000,
            fromEntity = "USR_02",
            toEntity = "USR_01",
        )

        val description = UndoEligibility(indiaDefinitions).undoDescription(session)

        assertNotNull(description)
        assertTrue(description!!.contains(MoneyFormatter.format(7000, indiaDefinitions)))
        assertFalse(description.contains("M7000"))
    }

    @Test
    fun zeroBalanceUsesFormatter() {
        val mapper = GameplayResultMapper(ukDefinitions)
        val session = AppTestSupport.newGame().copy(
            players = AppTestSupport.newGame().players.mapValues { (_, player) ->
                player.copy(balance = 0)
            },
        )

        val ui = mapper.mapPlayerInfo("USR_01", session)

        assertTrue(ui.primaryMessage.contains("Balance: ${MoneyFormatter.format(0, ukDefinitions)}"))
    }

    private fun customDefinitions(symbol: String): GameDefinitions {
        val base = ukDefinitions
        return base.copy(
            editionId = "test-custom",
            bankingValues = base.bankingValues.copy(
                currency = CurrencyDefinition(code = "TEST", symbol = symbol, scale = 1),
            ),
        )
    }

    private fun sessionWithLastTransaction(
        definitions: GameDefinitions,
        transactionType: TransactionType,
        amount: Int,
        fromEntity: String? = null,
        toEntity: String? = null,
        playerId: String? = null,
    ): GameSession {
        val base = newGame(definitions, DefaultGameEngine(definitions))
        return base.copy(
            undoSnapshot = base.snapshot(),
            transactions = listOf(
                Transaction(
                    transactionId = "TX_TEST",
                    gameId = base.gameId,
                    timestamp = 1L,
                    transactionType = transactionType,
                    fromEntity = fromEntity,
                    toEntity = toEntity,
                    playerId = playerId,
                    amount = amount,
                    reversible = true,
                ),
            ),
        )
    }

    private fun newGame(definitions: GameDefinitions, engine: DefaultGameEngine): GameSession {
        var result = engine.process(
            GameSession(gameId = "MONEY_FORMAT_TEST", editionId = definitions.editionId),
            GameCommand.CreateGame("MONEY_FORMAT_TEST"),
        )
        for (playerId in listOf("USR_01", "USR_02")) {
            result = engine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            )
        }
        result = engine.process(result.session, GameCommand.StartGame)
        return result.session
    }
}
