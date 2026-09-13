package com.boardbanker.app.persistence

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.persistence.repository.EditionAwareGameSessionRepository
import com.boardbanker.app.persistence.repository.SaveSessionResult
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayCelebrationSaveIntegrationTest {
    @Test
    fun applyEventWithContributorShortfallCommitsThroughEditionAwareRepository() = runTest {
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { AppTestSupport.editionRepository.load(it) },
            manifestLoader = { AppTestSupport.editionRepository.loadManifest(it) },
        )
        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val engine = DefaultGameEngine(AppTestSupport.editionRepository.load(EditionIds.INDIA))
        val session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        ).copy(
            players = AppTestSupport.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            ).players.mapValues { (id, player) ->
                when (id) {
                    "USR_01" -> player.copy(balance = 3000)
                    "USR_02" -> player.copy(balance = 25000)
                    "USR_03" -> player.copy(balance = 25000)
                    "USR_04" -> player.copy(balance = 25000)
                    else -> player
                }
            },
        )

        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, applied.outcome)

        val store = CommittedGameSessionStore(editionAware)
        val commit = store.commitGameResult(applied)

        assertTrue(commit is CommitResult.Persisted)
        val persisted = (commit as CommitResult.Persisted).session
        assertEquals(DebtReason.EVENT_CONTRIBUTOR, persisted.debtResolution!!.reason)
        assertEquals("USR_01", persisted.debtResolution!!.debtorPlayerId)
        assertNotNull(persisted.pendingEventMultiContributorSettlement)

        val reload = editionAware.loadLatestActive()
        assertTrue(reload is SavedGameLoadResult.Success)
        assertEquals(persisted, (reload as SavedGameLoadResult.Success).session)
    }

    @Test
    fun validationFailureReasonIsNotGenericSerializationError() = runTest {
        val repository = FakeGameSessionRepository()
        val orchestrator = SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { AppTestSupport.editionRepository.load(it) },
            manifestLoader = { AppTestSupport.editionRepository.loadManifest(it) },
        )
        val editionAware = EditionAwareGameSessionRepository(repository, repository, orchestrator)
        val engine = DefaultGameEngine(AppTestSupport.editionRepository.load(EditionIds.INDIA))
        val session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        ).copy(
            players = AppTestSupport.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            ).players.mapValues { (id, player) ->
                when (id) {
                    "USR_01" -> player.copy(balance = 3000)
                    else -> player.copy(balance = 25000)
                }
            },
        )
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))
        val save = editionAware.save(applied.session)

        assertTrue(save is SaveSessionResult.Success)
    }
}
