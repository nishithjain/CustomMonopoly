package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoJailLocationAuctionTests {
    private val engine = TestFixtures.engine

    @Test
    fun tsGo001_normalDiceMovementPassesGo() {
        val session = TestFixtures.newGame()
        val result = engine.process(session, GameCommand.PayGoSalary("USR_01"))
        assertEquals(1700, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun tsGo002_locationMovementNoGo() {
        val session = TestFixtures.newGame()
        val result = engine.process(
            session,
            GameCommand.PayLocationFee("USR_01", "PRP_10"),
        )
        assertFalse(result.transactions.any {
            it.transactionType == TransactionType.BANK_CREDIT && it.amount == 200
        })
    }

    @Test
    fun tsJail001_payM100ToExit() {
        var session = TestFixtures.sessionWithJail("USR_01")
        val result = engine.process(session, GameCommand.PayJailFee("USR_01"))
        assertEquals(1400, result.session.players["USR_01"]!!.balance)
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun tsJail002_jailedPlayerCannotBid() {
        var session = TestFixtures.newGame()
        session = session.copy(
            players = session.players + (
                "USR_02" to session.players["USR_02"]!!.copy(jailStatus = true)
            ),
            auction = com.boardbanker.core.model.AuctionState("PRP_03", startedByPlayerId = "USR_01"),
        )
        val result = engine.process(session, GameCommand.PlaceAuctionBid("USR_02", 20))
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun tsLocation001_payM100MoveToUnownedProperty() {
        val session = TestFixtures.newGame()
        val result = engine.process(
            session,
            GameCommand.PayLocationFee("USR_01", "PRP_10"),
        )
        assertEquals(1400, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.LOCATION_FEE })
    }

    @Test
    fun tsLocation002_payM100MoveToOwnProperty() {
        var session = TestFixtures.sessionWithProperty("PRP_05", "USR_01", 2)
        val result = engine.process(
            session,
            GameCommand.PayLocationFee("USR_01", "PRP_05"),
        )
        assertEquals(1400, result.session.players["USR_01"]!!.balance)
        assertEquals(3, result.session.properties["PRP_05"]!!.currentRentLevel)
    }

    @Test
    fun tsAuction001_fixedM20Increments() {
        var session = TestFixtures.newGame()
        session = engine.process(session, GameCommand.StartAuction("PRP_03", "USR_01")).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_01", 20)).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_02", 40)).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_01", 60)).session
        val result = engine.process(session, GameCommand.CompleteAuction)
        assertEquals("USR_01", result.session.properties["PRP_03"]!!.ownerPlayerId)
        assertEquals(1, result.session.properties["PRP_03"]!!.currentRentLevel)
        assertEquals(1440, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun tsAuction002_noBidsCancels() {
        var session = TestFixtures.newGame()
        session = engine.process(session, GameCommand.StartAuction("PRP_03", "USR_01")).session
        val result = engine.process(session, GameCommand.CancelAuction)
        assertNull(result.session.auction)
        assertNull(result.session.properties["PRP_03"]!!.ownerPlayerId)
    }
}
