package com.boardbanker.core.persistence

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionPersistenceScenarioTest {
    private val engine = TestFixtures.engine
    private val serializer = KotlinGameSessionSerializer()
    private val validator = SessionRestoreValidator(TestFixtures.definitions)

    @Test
    fun engineScenarioRoundTripsThroughSerializer() {
        var result = engine.process(GameSession(gameId = "SCENARIO_1"), GameCommand.CreateGame("SCENARIO_1"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = engine.process(result.session, GameCommand.StartGame)
        result = engine.process(result.session, GameCommand.PurchaseProperty("USR_01", "PRP_07"))
        result = engine.process(
            result.session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_07"),
        )
        result = engine.process(result.session, GameCommand.SendPlayerToJail("USR_02"))
        result = engine.process(result.session, GameCommand.ApplyEvent("USR_01", "EVT_01"))

        val original = result.session
        val restored = serializer.deserialize(serializer.serialize(original))
        assertEquals(original, restored)
        assertTrue(validator.isValid(restored))
    }

    @Test
    fun restoreValidatorRejectsUnknownProperty() {
        val session = TestFixtures.newGame().copy(
            properties = TestFixtures.newGame().properties + (
                "PRP_99" to com.boardbanker.core.model.PropertyState("PRP_99", "USR_01", 1)
            ),
        )
        assertTrue(validator.validate(session).any { it.contains("PRP_99") })
    }

    @Test
    fun rejectedEngineResultWouldNotBePersistedByCoordinatorContract() {
        val session = TestFixtures.sessionWithProperty("PRP_07", "USR_02", 1)
        val rejected = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_07"))
        assertEquals(GameOutcome.REJECTED, rejected.outcome)
    }
}
