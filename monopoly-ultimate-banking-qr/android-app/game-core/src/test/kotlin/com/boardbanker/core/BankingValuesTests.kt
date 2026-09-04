package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EventAmounts
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingValuesTests {
    private val definitions = TestFixtures.definitions
    private val banking = definitions.bankingValues

    @Test
    fun loadedBankingValuesMatchUltimateBankingEdition() {
        assertEquals(1500, banking.startingBalance)
        assertEquals(200, banking.goSalary)
        assertEquals(100, banking.locationFee)
        assertEquals(100, banking.jailReleaseFee)
        assertEquals(20, banking.auctionBidIncrement)
        assertEquals(50, banking.eventAmounts.m50)
        assertEquals(200, banking.eventAmounts.m200)
        assertEquals("M", banking.currency.code)
        assertEquals("M", banking.currency.symbol)
        assertEquals(1, banking.currency.scale)
    }

    @Test
    fun newGameStartingBalanceComesFromBankingValues() {
        val session = TestFixtures.newGame()
        session.players.values.forEach { player ->
            assertEquals(banking.startingBalance, player.balance)
        }
        assertEquals(1500, banking.startingBalance)
    }

    @Test
    fun moneyFormatterUsesLoadedCurrencySymbol() {
        assertEquals("M1500", MoneyFormatter.format(banking.startingBalance, definitions))
        assertEquals("M200", MoneyFormatter.format(banking.goSalary, definitions))
    }

    @Test
    fun customBankingValuesDriveEngineAmounts() {
        val custom = definitions.copy(
            bankingValues = banking.copy(
                startingBalance = 9999,
                goSalary = 777,
                locationFee = 333,
                jailReleaseFee = 444,
                auctionBidIncrement = 25,
                eventAmounts = EventAmounts(m50 = 55, m200 = 222),
            ),
        )
        val engine = DefaultGameEngine(custom)

        var session = engine.process(TestFixtures.emptySession("CUSTOM_BANKING"), GameCommand.CreateGame("CUSTOM_BANKING")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Aditya")).session
        session = engine.process(session, GameCommand.StartGame).session

        assertEquals(9999, session.players["USR_01"]!!.balance)
        assertEquals(9999, session.players["USR_02"]!!.balance)

        val afterGo = engine.process(session, GameCommand.PayGoSalary("USR_01")).session
        assertEquals(9999 + 777, afterGo.players["USR_01"]!!.balance)

        val afterLocation = engine.process(afterGo, GameCommand.PayLocationFee("USR_01", "PRP_10")).session
        assertEquals(9999 + 777 - 333, afterLocation.players["USR_01"]!!.balance)

        val jailed = afterLocation.copy(
            players = afterLocation.players + (
                "USR_01" to afterLocation.players["USR_01"]!!.copy(jailStatus = true)
            ),
        )
        val afterJail = engine.process(jailed, GameCommand.PayJailFee("USR_01")).session
        assertEquals(9999 + 777 - 333 - 444, afterJail.players["USR_01"]!!.balance)
        assertEquals(false, afterJail.players["USR_01"]!!.jailStatus)

        val auctionSession = engine.process(
            afterJail,
            GameCommand.StartAuction(propertyId = "PRP_12", startedByPlayerId = "USR_01"),
        ).session
        val firstBid = engine.process(auctionSession, GameCommand.PlaceAuctionBid("USR_02", 25))
        assertTrue(firstBid.isSuccess)
        assertEquals(25, firstBid.session.auction!!.currentBid)
        val secondBid = engine.process(firstBid.session, GameCommand.PlaceAuctionBid("USR_01", 50))
        assertTrue(secondBid.isSuccess)
        assertEquals(50, secondBid.session.auction!!.currentBid)
    }

    @Test
    fun customEventAmountsDriveStandardEventMoney() {
        val custom = definitions.copy(
            bankingValues = banking.copy(
                eventAmounts = EventAmounts(m50 = 55, m200 = 222),
            ),
        )
        val engine = DefaultGameEngine(custom)
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01")
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        val ownedCount = session.properties.values.count { it.ownerPlayerId == "USR_01" }
        val beforeHighway = session.players["USR_01"]!!.balance
        val afterHighway = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_01")).session
        assertEquals(beforeHighway - ownedCount * 55, afterHighway.players["USR_01"]!!.balance)

        val beforeCredit = afterHighway.players["USR_01"]!!.balance
        val beforeOther = afterHighway.players["USR_02"]!!.balance
        val afterCredit = engine.process(
            afterHighway,
            GameCommand.ApplyEvent("EVT_11", "USR_01", targetPlayerId = "USR_02"),
        ).session
        assertEquals(beforeCredit + 222, afterCredit.players["USR_01"]!!.balance)
        assertEquals(beforeOther + 222, afterCredit.players["USR_02"]!!.balance)
    }
}
