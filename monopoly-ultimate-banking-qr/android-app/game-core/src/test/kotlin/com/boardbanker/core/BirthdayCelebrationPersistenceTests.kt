package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import com.boardbanker.core.persistence.SessionRestoreValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BirthdayCelebrationPersistenceTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()
    private val restoreOrchestrator = SavedGameRestoreOrchestrator(
        serializer = serializer,
        editionLoader = { TestFixtures.loadEdition(it) },
        manifestLoader = { TestFixtures.loadEdition(it).edition!! },
    )

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun exactFourPlayerReproduction_savesAndRestoresPendingContributorDebt() {
        val session = reproductionSession()
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, applied.outcome)
        assertNull(restoreOrchestrator.validateForSave(applied.session))
        val debt = applied.session.debtResolution!!
        assertEquals(DebtReason.EVENT_CONTRIBUTOR, debt.reason)
        assertEquals("USR_01", debt.debtorPlayerId)
        assertEquals("USR_02", debt.eventContributorDebt!!.recipientPlayerId)
        assertEquals(5000, debt.eventContributorDebt!!.contributionAmount)
        assertEquals(3000, debt.eventContributorDebt!!.cashAvailable)
        assertEquals(2000, debt.eventContributorDebt!!.shortfall)
        assertNotNull(applied.session.pendingEventMultiContributorSettlement)
        assertNotNull(applied.session.pendingEventExecution)

        val json = serializer.serialize(applied.session)
        val restored = restoreOrchestrator.restore(
            com.boardbanker.core.persistence.RawSavedGame(
                sessionJson = json,
                schemaVersion = com.boardbanker.core.persistence.GameSessionSchema.CURRENT_VERSION,
            ),
        )
        assertTrue(restored is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val loaded = (restored as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(applied.session, loaded)
        assertTrue(SessionRestoreValidator(definitions).validate(loaded).isEmpty())
    }

    @Test
    fun exactFourPlayerReproduction_completesAfterPropertySaleAndPersistsSummary() {
        var session = reproductionSession().let { base ->
            base.copy(
                properties = base.properties + (
                    "PRP_05" to base.properties["PRP_05"]!!.copy(ownerPlayerId = "USR_01")
                ),
            )
        }
        val recipientBefore = session.players["USR_02"]!!.balance
        session = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02")).session
        assertNull(restoreOrchestrator.validateForSave(session))

        val settled = engine.process(session, GameCommand.ResolveDebtWithProperties(listOf("PRP_05")))
        assertEquals(GameOutcome.SUCCESS, settled.outcome)
        assertNull(restoreOrchestrator.validateForSave(settled.session))
        assertNull(settled.session.debtResolution)
        assertNull(settled.session.pendingEventExecution)
        assertNull(settled.session.properties["PRP_05"]!!.ownerPlayerId)
        assertEquals(recipientBefore + 15000, settled.session.players["USR_02"]!!.balance)

        val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(
            settled.session.transactions.single {
                it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
            },
        )!!
        assertEquals(listOf("USR_01", "USR_03", "USR_04"), metadata.transfers.map { it.fromPlayerId })
        assertEquals(15000, metadata.totalAmount)

        val restored = serializer.deserialize(serializer.serialize(settled.session))
        assertEquals(settled.session, restored)
    }

    @Test
    fun exactFourPlayerReproduction_undoBlockedWhileContributorDebtIsActive() {
        val session = reproductionSession()
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))
        val rejectedUndo = engine.process(applied.session, GameCommand.UndoLastAction)

        assertEquals(GameOutcome.REJECTED, rejectedUndo.outcome)
        assertNotNull(rejectedUndo.session.debtResolution)
        assertNotNull(rejectedUndo.session.pendingEventMultiContributorSettlement)
        assertNull(restoreOrchestrator.validateForSave(rejectedUndo.session))
    }

    private fun reproductionSession() = TestFixtures.newGameForEdition(
        EditionIds.INDIA,
        listOf("USR_01", "USR_02", "USR_03", "USR_04"),
        mapOf(
            "USR_01" to 3000,
            "USR_02" to 25000,
            "USR_03" to 25000,
            "USR_04" to 25000,
        ),
    )
}
