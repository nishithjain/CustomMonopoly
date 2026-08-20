package com.boardbanker.app.banking

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BankingResultMapperPlayerIconTest {
    private val mapper = BankingResultMapper(AppTestSupport.definitions)

    @Test
    fun auctionWinIncludesWinnerPlayerId() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(session, GameCommand.StartAuction("PRP_01", "USR_01")).session
        session = AppTestSupport.engine.process(session, GameCommand.PlaceAuctionBid("USR_02", 20)).session
        val result = AppTestSupport.engine.process(session, GameCommand.CompleteAuction)
        val winnerId = result.session.properties["PRP_01"]!!.ownerPlayerId!!
        val ui = mapper.mapAuctionWin(result, "PRP_01", winnerId)
        assertEquals("USR_02", ui.primaryPlayerId)
        assertEquals("Aditya", ui.primaryPlayerName)
    }

    @Test
    fun winnerResultIncludesWinnerAndRankings() {
        val session = AppTestSupport.newGame()
        val ui = mapper.mapWinner(session)
        assertNotNull(ui.primaryPlayerId)
        assertEquals(2, ui.playerRankings.size)
        assertEquals(setOf("USR_01", "USR_02"), ui.playerRankings.map { it.playerId }.toSet())
        assertEquals(setOf("Nishith", "Aditya"), ui.playerRankings.map { it.playerName }.toSet())
    }
}
