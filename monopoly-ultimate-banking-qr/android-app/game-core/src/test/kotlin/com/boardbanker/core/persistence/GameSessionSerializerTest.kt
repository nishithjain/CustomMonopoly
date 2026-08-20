package com.boardbanker.core.persistence

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.ColorGroupState
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.TemporaryEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionSerializerTest {
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun roundTripPreservesComplexSession() {
        val original = buildComplexSession()
        val restored = serializer.deserialize(serializer.serialize(original))
        assertEquals(original, restored)
    }

    @Test
    fun evt13TemporaryEffectSurvivesRoundTrip() {
        val original = buildComplexSession()
        val effect = original.temporaryEffects.first { it.createdByEventId == "EVT_13" }
        assertEquals(1, effect.remainingUses)
        val restored = serializer.deserialize(serializer.serialize(original))
        val restoredEffect = restored.temporaryEffects.first { it.createdByEventId == "EVT_13" }
        assertEquals(1, restoredEffect.remainingUses)
    }

    @Test
    fun jailStateSurvivesRoundTrip() {
        val original = buildComplexSession()
        assertTrue(original.players["USR_02"]!!.jailStatus)
        val restored = serializer.deserialize(serializer.serialize(original))
        assertTrue(restored.players["USR_02"]!!.jailStatus)
    }

    @Test
    fun propertyStateSurvivesRoundTrip() {
        val original = buildComplexSession()
        val property = original.properties["PRP_22"]!!
        assertEquals("USR_03", property.ownerPlayerId)
        assertEquals(4, property.currentRentLevel)
        val restored = serializer.deserialize(serializer.serialize(original))
        val restoredProperty = restored.properties["PRP_22"]!!
        assertEquals("USR_03", restoredProperty.ownerPlayerId)
        assertEquals(4, restoredProperty.currentRentLevel)
    }

    @Test
    fun colorGroupCompletionSurvivesRoundTrip() {
        val original = buildComplexSession()
        assertTrue(original.colorGroups["GREEN"]!!.completionBonusApplied)
        val restored = serializer.deserialize(serializer.serialize(original))
        assertTrue(restored.colorGroups["GREEN"]!!.completionBonusApplied)
    }

    @Test
    fun transactionHistorySurvivesRoundTrip() {
        val original = buildComplexSession()
        val restored = serializer.deserialize(serializer.serialize(original))
        assertEquals(original.transactions.size, restored.transactions.size)
        assertEquals(original.transactions.map { it.transactionId }, restored.transactions.map { it.transactionId })
    }

    @Test
    fun undoSnapshotSurvivesRoundTrip() {
        val original = buildComplexSession()
        assertNotNull(original.undoSnapshot)
        val restored = serializer.deserialize(serializer.serialize(original))
        assertEquals(original.undoSnapshot, restored.undoSnapshot)
    }

    @Test
    fun customPlayerNamesSurviveRoundTripInComplexSession() {
        val original = buildComplexSession()
        assertEquals("Nishith", original.players["USR_01"]!!.playerName)
        assertEquals("Aditya", original.players["USR_02"]!!.playerName)
        assertEquals("Rahul", original.players["USR_03"]!!.playerName)
        val restored = serializer.deserialize(serializer.serialize(original))
        assertEquals("Nishith", restored.players["USR_01"]!!.playerName)
        assertEquals("Aditya", restored.players["USR_02"]!!.playerName)
        assertEquals("Rahul", restored.players["USR_03"]!!.playerName)
    }

    private fun buildComplexSession(): GameSession {
        val engine = TestFixtures.engine
        var result = engine.process(
            GameSession(gameId = "PERSIST_TEST"),
            GameCommand.CreateGame("PERSIST_TEST"),
        )
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_03", "Rahul"))
        result = engine.process(result.session, GameCommand.StartGame)

        result = engine.process(
            result.session,
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        )

        var session = result.session.copy(
            players = result.session.players + (
                "USR_02" to result.session.players["USR_02"]!!.copy(jailStatus = true)
            ),
            properties = result.session.properties + (
                "PRP_22" to PropertyState(
                    propertyId = "PRP_22",
                    ownerPlayerId = "USR_03",
                    currentRentLevel = 4,
                )
            ),
            undoSnapshot = result.session.snapshot(),
        )

        result = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        session = result.session

        result = engine.process(session, GameCommand.ApplyEvent("USR_01", "EVT_01"))
        return result.session.copy(
            colorGroups = result.session.colorGroups + (
                "GREEN" to ColorGroupState(colorGroup = "GREEN", completionBonusApplied = true)
            ),
            temporaryEffects = listOf(
                TemporaryEffect(
                    effectId = "EVT_13_EFFECT",
                    effectType = "FORCE_LEVEL_1_RENT",
                    remainingUses = 1,
                    createdByEventId = "EVT_13",
                ),
            ),
        )
    }
}
