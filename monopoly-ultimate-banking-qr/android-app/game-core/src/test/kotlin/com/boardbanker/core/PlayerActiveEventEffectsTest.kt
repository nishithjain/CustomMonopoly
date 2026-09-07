package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventTargetType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.TemporaryEffect
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.rules.PlayerActiveEventEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerActiveEventEffectsTest {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun rentReliefShowsForAffectedPlayerOnly() {
        val session = engine.process(
            TestFixtures.indiaGame(),
            GameCommand.ApplyEvent("EVT_10", "USR_01"),
        ).session

        assertEquals(listOf("Rent Relief"), activeNames(session, "USR_01"))
        assertTrue(activeNames(session, "USR_02").isEmpty())
    }

    @Test
    fun multipleActiveEffectsAreReturned() {
        var session = TestFixtures.indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session
        session = engine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01")).session

        assertEquals(
            listOf("Rent Relief", "Second Wind"),
            activeNames(session, "USR_01"),
        )
    }

    @Test
    fun consumedRentReliefIsRemoved() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = TestFixtures.sessionWithProperty("PRP_15", "USR_01", players = listOf("USR_01", "USR_02"))
            .copy(
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = session.editionDefinitionVersion,
                players = session.players,
            )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        session = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15")).session

        assertTrue(activeNames(session, "USR_02").isEmpty())
    }

    @Test
    fun undoRestoresRentReliefDisplay() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = TestFixtures.sessionWithProperty("PRP_15", "USR_01", players = listOf("USR_01", "USR_02"))
            .copy(
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = session.editionDefinitionVersion,
                players = session.players,
            )
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        session = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15")).session
        session = engine.process(session, GameCommand.UndoLastAction).session

        assertEquals(listOf("Rent Relief"), activeNames(session, "USR_02"))
    }

    @Test
    fun resumeGameRestoresActiveEventNames() {
        var session = TestFixtures.indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01")).session

        val restored = serializer.deserialize(serializer.serialize(session))

        assertEquals(listOf("Get Out of Jail Pass"), activeNames(restored, "USR_01"))
    }

    @Test
    fun eventNamesComeFromEditionDefinitions() {
        val ukDefinitions = TestFixtures.loadEdition(EditionIds.UK)
        val ukEngine = DefaultGameEngine(ukDefinitions)
        val session = ukEngine.process(
            TestFixtures.newGameForEdition(EditionIds.UK, listOf("USR_01", "USR_02")),
            GameCommand.ApplyEvent("EVT_13", "USR_01"),
        ).session

        assertEquals(listOf("On The Run"), activeNames(session, "USR_01", ukDefinitions))
        assertEquals(listOf("On The Run"), activeNames(session, "USR_02", ukDefinitions))
    }

    @Test
    fun customEventIdResolvesWithoutHardCoding() {
        val customDefinitions = definitionsWithCustomRentRelief("CUSTOM_RENT_EVT", "Custom Rent Shield")
        val customEngine = DefaultGameEngine(customDefinitions)
        val session = customEngine.process(
            TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
                .copy(editionId = EditionIds.INDIA, editionDefinitionVersion = customDefinitions.edition!!.definitionVersion),
            GameCommand.ApplyEvent("CUSTOM_RENT_EVT", "USR_01"),
        ).session

        assertEquals(listOf("Custom Rent Shield"), activeNames(session, "USR_01", customDefinitions))
    }

    @Test
    fun immediatePaymentEventDoesNotRemainVisible() {
        val session = engine.process(
            TestFixtures.indiaGame(),
            GameCommand.ApplyEvent("EVT_09", "USR_01"),
        ).session

        assertTrue(activeNames(session, "USR_01").isEmpty())
    }

    @Test
    fun skipNextTurnShowsUntilProcessed() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01")).session
        assertEquals(listOf("Metro Delay"), activeNames(session, "USR_01"))

        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals(listOf("Metro Delay"), activeNames(session, "USR_01"))

        session = TestFixtures.endTurn(session, engine = engine).session
        assertFalse(session.players["USR_01"]!!.pendingSkipTurnCount > 0)
        assertTrue(activeNames(session, "USR_01").isEmpty())
    }

    private fun activeNames(
        session: com.boardbanker.core.model.GameSession,
        playerId: String,
        defs: GameDefinitions = definitions,
    ): List<String> = PlayerActiveEventEffects.activeEventNames(playerId, session, defs)

    private fun definitionsWithCustomRentRelief(eventId: String, eventName: String): GameDefinitions {
        val base = TestFixtures.loadEdition(EditionIds.INDIA)
        val customEvent = EventDefinition(
            eventId = eventId,
            name = eventName,
            qrPayload = "MUB:CUSTOM:01",
            actions = listOf(
                EventActionDefinition(
                    actionType = "NEXT_RENT_WAIVER",
                    targetType = EventTargetType.CURRENT_PLAYER.name,
                ),
            ),
        )
        return base.copy(
            events = base.events + (eventId to customEvent),
        )
    }
}
