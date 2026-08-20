package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.TemporaryEffect
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTests {
    private val engine = TestFixtures.engine

    @Test
    fun tsEvt008_houseParty() {
        var session = TestFixtures.newGame()
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_05" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_04" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 3)
                    else -> state
                }
            },
        )
        val result = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_08", "USR_01", propertyId = "PRP_05"),
        )
        assertEquals(3, result.session.properties["PRP_05"]!!.currentRentLevel)
        assertEquals(2, result.session.properties["PRP_04"]!!.currentRentLevel)
        assertEquals(1, result.session.properties["PRP_06"]!!.currentRentLevel)
    }

    @Test
    fun tsEvt013_onTheRun() {
        var session = TestFixtures.sessionWithTemporaryEffect(
            TemporaryEffect(
                effectId = "EVT_13_EFFECT",
                effectType = "FORCE_LEVEL_1_RENT",
                remainingUses = 2,
                createdByEventId = "EVT_13",
            ),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_20" to session.properties["PRP_20"]!!.copy(
                    ownerPlayerId = "USR_01",
                    currentRentLevel = 4,
                )
            ),
        )
        val level1Rent = TestFixtures.rentAmount("PRP_20", 1)
        val result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_20"),
        )
        assertEquals(level1Rent, result.transactions.first { it.transactionType == TransactionType.RENT_PAYMENT }.amount)
        assertEquals(5, result.session.properties["PRP_20"]!!.currentRentLevel)
        assertEquals(1, result.session.temporaryEffects.first().remainingUses)
    }

    @Test
    fun tsEvt013Owner_ownerLandingDoesNotConsume() {
        var session = TestFixtures.sessionWithTemporaryEffect(
            TemporaryEffect(
                effectId = "EVT_13_EFFECT",
                effectType = "FORCE_LEVEL_1_RENT",
                remainingUses = 2,
                createdByEventId = "EVT_13",
            ),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(
                    ownerPlayerId = "USR_01",
                    currentRentLevel = 3,
                )
            ),
        )
        val result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"),
        )
        assertEquals(2, result.session.temporaryEffects.first().remainingUses)
    }

    @Test
    fun tsEvt015_boardSideDecrease() {
        var session = TestFixtures.newGame()
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 3)
                    "PRP_05" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_06" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 3)
                    else -> state
                }
            },
        )
        val result = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_15", "USR_01", propertyId = "PRP_03"),
        )
        assertEquals(2, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertEquals(1, result.session.properties["PRP_05"]!!.currentRentLevel)
        assertEquals(3, result.session.properties["PRP_06"]!!.currentRentLevel)
    }

    @Test
    fun tsEvt021_totalGridlock() {
        var session = TestFixtures.newGame()
        session = session.copy(
            players = session.players.mapValues { (id, p) ->
                if (id == "USR_02") p.copy(jailStatus = true) else p
            },
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_21", "USR_01"))
        assertTrue(result.physicalActions.isNotEmpty())
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun tsEvt022_boardSideIncrease() {
        var session = TestFixtures.newGame()
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_20" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
                    "PRP_22" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 5)
                    else -> state
                }
            },
        )
        val result = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_22", "USR_01", propertyId = "PRP_18"),
        )
        assertEquals(5, result.session.properties["PRP_20"]!!.currentRentLevel)
        assertEquals(5, result.session.properties["PRP_22"]!!.currentRentLevel)
        assertEquals(1, result.session.properties["PRP_19"]!!.currentRentLevel)
    }

    @Test
    fun all23EventsApplySuccessfully() {
        val eventIds = (1..23).map { "EVT_${it.toString().padStart(2, '0')}" }
        for (eventId in eventIds) {
            var session = prepareSessionForEvent(eventId)
            val command = buildEventCommand(eventId)
            val result = engine.process(session, command)
            assertTrue("Event $eventId failed: ${result.error}", result.isSuccess)
            assertTrue(
                "Event $eventId should record EVENT_APPLIED or have no effect",
                result.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED } ||
                    result.transactions.isEmpty(),
            )
        }
    }

    private fun prepareSessionForEvent(eventId: String): com.boardbanker.core.model.GameSession {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02", "USR_03"))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when {
                    id in listOf("PRP_01", "PRP_02", "PRP_05", "PRP_06") ->
                        state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    id in listOf("PRP_08", "PRP_09") ->
                        state.copy(ownerPlayerId = "USR_02", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        return session
    }

    private fun buildEventCommand(eventId: String): GameCommand.ApplyEvent = when (eventId) {
        "EVT_01", "EVT_03", "EVT_18" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_10")
        "EVT_02", "EVT_12", "EVT_17" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01")
        "EVT_04", "EVT_16", "EVT_20" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01")
        "EVT_05" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01")
        "EVT_06", "EVT_09" ->
            GameCommand.ApplyEvent(
                eventId, "USR_01",
                targetPlayerId = "USR_02",
                propertyId = "PRP_01",
                secondPropertyId = "PRP_08",
            )
        "EVT_07" ->
            GameCommand.ApplyEvent(eventId, "USR_01")
        "EVT_08", "EVT_10" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_05")
        "EVT_11", "EVT_23" ->
            GameCommand.ApplyEvent(eventId, "USR_01", targetPlayerId = "USR_02")
        "EVT_13", "EVT_21" ->
            GameCommand.ApplyEvent(eventId, "USR_01")
        "EVT_14" ->
            GameCommand.ApplyEvent(eventId, "USR_01", targetPlayerId = "USR_02")
        "EVT_15", "EVT_22" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_03")
        "EVT_19" ->
            GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01")
        else -> GameCommand.ApplyEvent(eventId, "USR_01")
    }
}
