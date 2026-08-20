package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyAndRentTests {
    private val engine = TestFixtures.engine
    private val definitions = TestFixtures.definitions

    @Test
    fun tsProperty001_buyUnownedProperty() {
        val session = TestFixtures.newGame()
        val result = engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        )
        assertEquals(1440, result.session.players["USR_01"]!!.balance)
        assertEquals("USR_01", result.session.properties["PRP_01"]!!.ownerPlayerId)
        assertEquals(1, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE })
    }

    @Test
    fun tsRent001_visitorPaysThenLevelIncreases() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 3)
        val rent = TestFixtures.rentAmount("PRP_01", 3)
        val result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        assertEquals(rent, result.transactions.first { it.transactionType == TransactionType.RENT_PAYMENT }.amount)
        assertEquals(1500 - rent, result.session.players["USR_02"]!!.balance)
        assertEquals(1500 + rent, result.session.players["USR_01"]!!.balance)
        assertEquals(4, result.session.properties["PRP_01"]!!.currentRentLevel)
    }

    @Test
    fun tsRent002_ownerLandsNoRentLevelIncreases() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 3)
        val result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"),
        )
        assertFalse(result.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertEquals(4, result.session.properties["PRP_01"]!!.currentRentLevel)
    }

    @Test
    fun tsRent003_jailedOwnerNoRentCollected() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 3)
        session = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(jailStatus = true)
            ),
        )
        val result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        assertFalse(result.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertEquals(3, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertEquals(1500, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun tsRent004_maximumLevelClamp() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 5)
        val ownerResult = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"),
        )
        assertEquals(5, ownerResult.session.properties["PRP_01"]!!.currentRentLevel)

        session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 5)
        val visitorResult = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        assertEquals(5, visitorResult.session.properties["PRP_01"]!!.currentRentLevel)
    }
}
