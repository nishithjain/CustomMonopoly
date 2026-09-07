package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RentReliefTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun activePlayerLandingOnOwnedPropertyWithRentReliefWaivesRent() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = TestFixtures.sessionWithProperty(
            propertyId = "PRP_15",
            ownerId = "USR_01",
            players = listOf("USR_01", "USR_02"),
        ).copy(
            editionId = EditionIds.INDIA,
            editionDefinitionVersion = session.editionDefinitionVersion,
            players = session.players,
        )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val expectedRent = definitions.properties["PRP_15"]!!
            .rentLevels.first { it.level == 1 }.amount
        val payerBefore = session.players["USR_02"]!!.balance
        val ownerBefore = session.players["USR_01"]!!.balance
        val levelBefore = session.properties["PRP_15"]!!.currentRentLevel

        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))

        assertTrue(result.isSuccess)
        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertEquals(payerBefore, result.session.players["USR_02"]!!.balance)
        assertEquals(ownerBefore, result.session.players["USR_01"]!!.balance)
        assertEquals(levelBefore, result.session.properties["PRP_15"]!!.currentRentLevel)
        assertFalse(result.session.players["USR_02"]!!.pendingRentWaiver)
        assertEquals(null, result.session.players["USR_02"]!!.rentWaiverSourceEventId)
        assertEquals("USR_02", result.session.turnState?.activePlayerId)
        val waivedTx = result.transactions.single { it.transactionType == TransactionType.RENT_WAIVED }
        assertEquals(expectedRent, waivedTx.amount)
        assertEquals("USR_02", waivedTx.fromEntity)
        assertEquals("USR_01", waivedTx.toEntity)
        assertEquals("EVT_10", waivedTx.eventId)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_PAYMENT })
    }

    @Test
    fun rentReliefNotConsumedOnUnownedProperty() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)

        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))

        assertEquals(GameOutcome.PENDING_ACTION, result.outcome)
        assertTrue(result.session.players["USR_02"]!!.pendingRentWaiver)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_WAIVED })
    }

    @Test
    fun rentReliefNotConsumedOnOwnProperty() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session
        session = TestFixtures.sessionWithProperty("PRP_15", "USR_01", players = listOf("USR_01", "USR_02"))
            .copy(
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = session.editionDefinitionVersion,
                players = session.players,
            )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_01", engine)

        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_15"))

        assertTrue(result.isSuccess)
        assertTrue(result.session.players["USR_01"]!!.pendingRentWaiver)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_WAIVED })
    }

    @Test
    fun rentReliefUndoRestoresEffectAndRemovesHistoryEntry() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = TestFixtures.sessionWithProperty("PRP_15", "USR_01", players = listOf("USR_01", "USR_02"))
            .copy(
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = session.editionDefinitionVersion,
                players = session.players,
            )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        session = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15")).session
        assertFalse(session.players["USR_02"]!!.pendingRentWaiver)

        val undone = engine.process(session, GameCommand.UndoLastAction)

        assertTrue(undone.isSuccess)
        assertTrue(undone.session.players["USR_02"]!!.pendingRentWaiver)
        assertEquals("EVT_10", undone.session.players["USR_02"]!!.rentWaiverSourceEventId)
        assertEquals(TransactionType.UNDO, undone.session.transactions.last().transactionType)
    }

    @Test
    fun withoutRentReliefNormalRentIsTransferred() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = session.copy(
            properties = session.properties + (
                "PRP_15" to session.properties["PRP_15"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val payerBefore = session.players["USR_02"]!!.balance
        val ownerBefore = session.players["USR_01"]!!.balance
        val expectedRent = definitions.properties["PRP_15"]!!
            .rentLevels.first { it.level == 1 }.amount

        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))

        assertEquals(payerBefore - expectedRent, result.session.players["USR_02"]!!.balance)
        assertEquals(ownerBefore + expectedRent, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT })
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_WAIVED })
    }
}
