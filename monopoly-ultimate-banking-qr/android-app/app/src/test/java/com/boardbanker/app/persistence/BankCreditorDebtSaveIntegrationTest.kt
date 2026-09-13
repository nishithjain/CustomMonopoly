package com.boardbanker.app.persistence

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.persistence.repository.EditionAwareGameSessionRepository
import com.boardbanker.app.persistence.repository.SaveSessionResult
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCreditorDebtSaveIntegrationTest {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)
    private val repository = FakeGameSessionRepository()
    private val editionAware = EditionAwareGameSessionRepository(
        repository,
        repository,
        SavedGameRestoreOrchestrator(
            serializer = KotlinGameSessionSerializer(),
            editionLoader = { AppTestSupport.editionRepository.load(it) },
            manifestLoader = { AppTestSupport.editionRepository.loadManifest(it) },
        ),
    )

    @Test
    fun festivalContributionShortfallCommitsWithoutSaveError() = runTest {
        val session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        ).copy(
            players = AppTestSupport.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            ).players.mapValues { (id, player) ->
                if (id == "USR_01") player.copy(balance = 4000) else player.copy(balance = 10000)
            },
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(DebtReason.EVENT, result.session.debtResolution!!.reason)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertNotNull(result.session.debtResolution!!.eventDebt)
        assertNotNull(result.session.pendingEventExecution)

        val commit = CommittedGameSessionStore(editionAware).commitGameResult(result)
        assertTrue(commit is CommitResult.Persisted)
    }

    @Test
    fun hospitalExpenseShortfallCommitsWithoutSaveError() = runTest {
        val result = engine.process(
            baseSession().copy(
                players = baseSession().players.mapValues { (id, player) ->
                    if (id == "USR_01") player.copy(balance = 3000) else player
                },
            ),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        )
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertNotNull(result.session.debtResolution!!.eventBankDebit)

        val commit = CommittedGameSessionStore(editionAware).commitGameResult(result)
        assertTrue(commit is CommitResult.Persisted)
    }

    @Test
    fun municipalMaintenanceShortfallCommitsWithoutSaveError() = runTest {
        val session = baseSession().let { base ->
            base.copy(
                properties = base.properties + (
                    "PRP_01" to base.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
                ),
            )
        }
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)

        val commit = CommittedGameSessionStore(editionAware).commitGameResult(result)
        assertTrue(commit is CommitResult.Persisted)
    }

    @Test
    fun payJailFeeShortfallCommitsWithoutSaveError() = runTest {
        val session = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
        ).copy(
            players = AppTestSupport.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
            ).players.mapValues { (id, player) ->
                when (id) {
                    "USR_01" -> player.copy(balance = 1000, jailStatus = true)
                    else -> player
                }
            },
        )
        val result = engine.process(session, GameCommand.PayJailFee("USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(DebtReason.JAIL, result.session.debtResolution!!.reason)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)

        val commit = CommittedGameSessionStore(editionAware).commitGameResult(result)
        assertTrue(commit is CommitResult.Persisted)
    }

    @Test
    fun cloudStorageShortfallCommitsWithoutSaveError() = runTest {
        val result = engine.process(baseSession(), GameCommand.ApplyEvent("EVT_22", "USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)

        val save = editionAware.save(result.session)
        assertTrue(save is SaveSessionResult.Success)
    }

    private fun baseSession() = AppTestSupport.newGameForEdition(
        EditionIds.INDIA,
        listOf("USR_01", "USR_02"),
    ).copy(
        players = AppTestSupport.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
        ).players.mapValues { (id, player) ->
            if (id == "USR_01") player.copy(balance = 1000) else player.copy(balance = 25000)
        },
    )
}
