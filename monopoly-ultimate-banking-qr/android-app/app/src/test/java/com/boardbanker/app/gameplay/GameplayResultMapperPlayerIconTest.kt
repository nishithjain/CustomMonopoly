package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.core.command.GameCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayResultMapperPlayerIconTest {
    private val mapper = GameplayResultMapper(AppTestSupport.definitions)

    @Test
    fun purchaseResultIncludesBuyerPlayerId() {
        val session = AppTestSupport.newGame()
        val before = session.players["USR_01"]!!.balance
        val result = AppTestSupport.engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        )
        val ui = mapper.mapPurchaseResult(result, "USR_01", "PRP_01", before)
        assertEquals("USR_01", ui.primaryPlayerId)
        assertEquals("Nishith", ui.primaryPlayerName)
    }

    @Test
    fun rentResultIncludesPayerAndOwnerPlayerIds() {
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
        assertEquals("USR_02", ui.primaryPlayerId)
        assertEquals("Aditya", ui.primaryPlayerName)
        assertEquals("USR_01", ui.secondaryPlayerId)
        assertEquals("Nishith", ui.secondaryPlayerName)
    }

    @Test
    fun playerInfoIncludesPlayerId() {
        val session = AppTestSupport.newGame()
        val ui = mapper.mapPlayerInfo("USR_01", session)
        assertEquals("USR_01", ui.primaryPlayerId)
        assertEquals("Nishith", ui.primaryPlayerName)
    }
}
