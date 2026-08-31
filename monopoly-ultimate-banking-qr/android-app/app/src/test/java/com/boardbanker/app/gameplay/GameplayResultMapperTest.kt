package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayResultMapperTest {
    private val definitions = AppTestSupport.definitions
    private val mapper = GameplayResultMapper(definitions)

    @Test
    fun purchaseResultShowsBalanceAndRent() {
        val session = AppTestSupport.newGame()
        val before = session.players["USR_01"]!!.balance
        val result = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        )
        val ui = mapper.mapPurchaseResult(result, "USR_01", "PRP_01", before)
        val balanceAfter = result.session.players["USR_01"]!!.balance
        assertEquals("PRP_01", ui.displayCardId)
        assertTrue(ui.primaryMessage.contains("Balance: ${MoneyFormatter.format(balanceAfter, definitions)}"))
        assertTrue(ui.primaryMessage.contains("[1] Old Kent Road"))
    }

    @Test
    fun rentResultShowsPayment() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session
        val before = session
        val result = AppTestSupport.engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        val ui = mapper.mapPropertyLandingResult(result, "USR_02", "PRP_01", before)
        assertEquals("RENT PAID", ui.title)
    }
}
