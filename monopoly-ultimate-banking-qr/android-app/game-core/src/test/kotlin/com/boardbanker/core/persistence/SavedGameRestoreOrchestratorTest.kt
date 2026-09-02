package com.boardbanker.core.persistence

import com.boardbanker.core.TestEditionResources
import com.boardbanker.core.TestFixtures
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PropertyState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameRestoreOrchestratorTest {
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun ukSaveLoadsUkDefinitionsBeforeValidation() {
        val orchestrator = orchestratorFor(TestFixtures.definitions.editionId)
        val session = ukSession()
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.Success)
        assertEquals(1, orchestrator.editionLoadCount)
        assertEquals(1, orchestrator.semanticValidationCount)
    }

    @Test
    fun indiaSaveLoadsIndiaDefinitionsNotUk() {
        val indiaDefinitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val orchestrator = orchestratorFor(
            editionLoader = { editionId ->
                assertEquals(EditionIds.INDIA, editionId)
                indiaDefinitions
            },
            manifestLoader = { editionId ->
                indiaDefinitions.edition!!
            },
        )
        val session = indiaSession()
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.Success)
        assertEquals(EditionIds.INDIA, (result as SavedGameLoadResult.Success).session.editionId)
    }

    @Test
    fun customEditionWithDifferentIdsRestoresSuccessfully() {
        val customDefinitions = loadCustomTestEdition()
        val orchestrator = orchestratorFor(
            editionLoader = { customDefinitions },
            manifestLoader = { customDefinitions.edition!! },
        )
        val session = customSession()
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.Success)
        assertEquals("custom-test", (result as SavedGameLoadResult.Success).session.editionId)
        assertTrue(result.session.properties.containsKey("CTP_01"))
    }

    @Test
    fun customEditionSaveFailsWhenValidatedAgainstUkDefinitions() {
        val session = customSession()
        val ukProblems = SessionRestoreValidator(TestFixtures.definitions).validate(session)
        assertTrue(ukProblems.any { it.contains("CTP_01") })
    }

    @Test
    fun unknownPropertyInCustomEditionFailsAfterCustomValidation() {
        val customDefinitions = loadCustomTestEdition()
        val orchestrator = orchestratorFor(
            editionLoader = { customDefinitions },
            manifestLoader = { customDefinitions.edition!! },
        )
        val session = customSession().copy(
            properties = mapOf("CTP_MISSING" to PropertyState("CTP_MISSING", "USR_01", 1)),
        )
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.SessionValidationFailed)
        assertTrue((result as SavedGameLoadResult.SessionValidationFailed).reason.contains("CTP_MISSING"))
    }

    @Test
    fun customEditionRestoreLoadsSavedEditionIdNotUk() {
        val loadedEditionIds = mutableListOf<String>()
        val customDefinitions = loadCustomTestEdition()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = serializer,
            editionLoader = { editionId ->
                loadedEditionIds += editionId
                customDefinitions
            },
            manifestLoader = { customDefinitions.edition!! },
        )
        val result = orchestrator.restore(raw(customSession()))

        assertTrue(result is SavedGameLoadResult.Success)
        assertEquals(listOf("custom-test"), loadedEditionIds)
    }

    @Test
    fun versionMismatchStopsBeforeSemanticValidation() {
        var validationInvoked = false
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = serializer,
            editionLoader = { TestFixtures.definitions },
            manifestLoader = {
                TestFixtures.definitions.edition!!.copy(definitionVersion = 2)
            },
            sessionRestoreValidatorFactory = {
                validationInvoked = true
                SessionRestoreValidator(it)
            },
        )
        val session = ukSession()
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.IncompatibleEditionVersion)
        assertEquals(0, orchestrator.semanticValidationCount)
        assertEquals(false, validationInvoked)
    }

    @Test
    fun unknownEditionDoesNotFallBackToUk() {
        val orchestrator = orchestratorFor(
            editionLoader = { throw IllegalArgumentException("Edition package missing") },
            manifestLoader = { throw IllegalArgumentException("Edition package missing") },
        )
        val session = GameSession(
            gameId = "UNKNOWN_EDITION",
            editionId = "missing-edition",
            editionDefinitionVersion = 1,
            status = GameStatus.ACTIVE,
        )
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.MissingEdition)
        assertEquals("missing-edition", (result as SavedGameLoadResult.MissingEdition).editionId)
    }

    @Test
    fun corruptedJsonFailsBeforeEditionLoading() {
        val orchestrator = orchestratorFor(TestFixtures.definitions.editionId)
        val result = orchestrator.restore(
            RawSavedGame(sessionJson = "{bad-json", schemaVersion = GameSessionSchema.CURRENT_VERSION),
        )

        assertTrue(result is SavedGameLoadResult.Corrupted)
        assertEquals(0, orchestrator.editionLoadCount)
    }

    @Test
    fun indiaSessionValidatedWithIndiaDefinitionsOnSave() {
        val indiaDefinitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val orchestrator = orchestratorFor(
            editionLoader = { indiaDefinitions },
            manifestLoader = { indiaDefinitions.edition!! },
        )
        val session = indiaSession()
        assertEquals(null, orchestrator.validateForSave(session))
    }

    @Test
    fun activeGameMissingTurnStateFailsRestoreValidation() {
        val indiaDefinitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val orchestrator = orchestratorFor(
            editionLoader = { indiaDefinitions },
            manifestLoader = { indiaDefinitions.edition!! },
        )
        val session = indiaSession().copy(turnState = null)
        val result = orchestrator.restore(raw(session))

        assertTrue(result is SavedGameLoadResult.SessionValidationFailed)
        assertTrue((result as SavedGameLoadResult.SessionValidationFailed).reason.contains("missing turn state"))
    }

    private fun orchestratorFor(
        editionId: String = EditionIds.UK,
        editionLoader: (String) -> com.boardbanker.core.model.GameDefinitions = { id ->
            TestFixtures.loadEdition(id)
        },
        manifestLoader: (String) -> EditionDefinition = { id ->
            TestFixtures.loadEdition(id).edition!!
        },
    ): SavedGameRestoreOrchestrator =
        SavedGameRestoreOrchestrator(
            serializer = serializer,
            editionLoader = editionLoader,
            manifestLoader = manifestLoader,
        )

    private fun ukSession(): GameSession {
        var result = TestFixtures.engine.process(
            GameSession(gameId = "UK_RESTORE", editionId = EditionIds.UK, editionDefinitionVersion = 1),
            GameCommand.CreateGame("UK_RESTORE"),
        )
        result = TestFixtures.engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = TestFixtures.engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = TestFixtures.engine.process(result.session, GameCommand.StartGame)
        return result.session
    }

    private fun indiaSession(): GameSession {
        val indiaEngine = com.boardbanker.core.engine.DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
        var result = indiaEngine.process(
            GameSession(gameId = "INDIA_RESTORE", editionId = EditionIds.INDIA, editionDefinitionVersion = 2),
            GameCommand.CreateGame("INDIA_RESTORE"),
        )
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = indiaEngine.process(result.session, GameCommand.StartGame)
        return result.session
    }

    private fun customSession(): GameSession {
        val customDefinitions = loadCustomTestEdition()
        val engine = com.boardbanker.core.engine.DefaultGameEngine(customDefinitions)
        var result = engine.process(
            GameSession(gameId = "CUSTOM_RESTORE", editionId = "custom-test", editionDefinitionVersion = 1),
            GameCommand.CreateGame("CUSTOM_RESTORE"),
        )
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = engine.process(result.session, GameCommand.StartGame)
        result = engine.process(result.session, GameCommand.PurchaseProperty("USR_01", "CTP_01"))
        return result.session
    }

    private fun raw(session: GameSession): RawSavedGame =
        RawSavedGame(
            sessionJson = serializer.serialize(session),
            schemaVersion = GameSessionSchema.CURRENT_VERSION,
        )

    private fun loadCustomTestEdition() = TestEditionResources.loadCustomTestEdition()
}
