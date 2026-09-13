package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventResolution
import com.boardbanker.core.model.EventResolutionPhase
import com.boardbanker.core.model.PendingEventExecution
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventPreviewCancellationTests {
    private lateinit var engine: DefaultGameEngine

    @Before
    fun setUp() {
        engine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
    }

    @Test
    fun cancellingEachPaymentEventBeforeApplyClearsOnlyPreview() {
        listOf("EVT_07", "EVT_06", "EVT_05", "EVT_08").forEach { eventId ->
            val session = TestFixtures.sessionWithActivePlayer(
                TestFixtures.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
                mapOf("USR_01" to 1000, "USR_02" to 9000),
                ),
                "USR_02",
                engine,
            )
            val beforeBalances = session.players.mapValues { it.value.balance }
            val cancelled = engine.process(
                session,
                GameCommand.CancelEventPreview(eventId, "USR_02"),
            )

            assertEquals(GameOutcome.SUCCESS, cancelled.outcome)
            assertEquals(beforeBalances, cancelled.session.players.mapValues { it.value.balance })
            assertNull(cancelled.session.pendingEventChoice)
            assertNull(cancelled.session.pendingEventExecution)
            assertNull(cancelled.session.pendingEventResolution)
            assertNull(cancelled.session.pendingEventMultiContributorSettlement)
            assertNull(cancelled.session.debtResolution)
            assertTrue(cancelled.session.transactions.none { it.eventId == eventId })
            assertEquals(GameOutcome.SUCCESS, engine.process(
                cancelled.session,
                GameCommand.EndTurn("USR_02"),
            ).outcome)
        }
    }

    @Test
    fun cancellingAfterInsufficientApplyCannotDiscardGenuineDebt() {
        val session = TestFixtures.sessionWithActivePlayer(
            TestFixtures.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
                mapOf("USR_01" to 1000, "USR_02" to 3000),
            ),
            "USR_02",
            engine,
        )
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_02"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, applied.outcome)
        assertTrue(applied.session.debtResolution != null)

        val cancelled = engine.process(
            applied.session,
            GameCommand.CancelEventPreview("EVT_05", "USR_02"),
        )
        assertEquals(GameOutcome.REJECTED, cancelled.outcome)
        assertTrue(cancelled.session.debtResolution != null)
    }

    @Test
    fun cancellingRepairsOrphanedUncommittedEventState() {
        val session = TestFixtures.sessionWithActivePlayer(
            TestFixtures.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
            ),
            "USR_02",
            engine,
        ).copy(
            pendingEventExecution = PendingEventExecution("EVT_05", "USR_02", 0),
            pendingEventResolution = EventResolution(
                resolutionId = "ORPHAN_RESOLUTION",
                eventId = "EVT_05",
                actingPlayerId = "USR_02",
                obligations = emptyList(),
                phase = EventResolutionPhase.AWAITING_DEBT,
            ),
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "BANK",
                amountRemaining = 1000,
                reason = DebtReason.EVENT,
            ),
        )
        val repaired = engine.process(
            session,
            GameCommand.CancelEventPreview("EVT_05", "USR_02"),
        )

        assertEquals(GameOutcome.SUCCESS, repaired.outcome)
        assertNull(repaired.session.pendingEventExecution)
        assertNull(repaired.session.pendingEventResolution)
        assertNull(repaired.session.debtResolution)
        assertFalse(repaired.session.transactions.any { it.eventId == "EVT_05" })
    }

    @Test
    fun committedEventCannotBeCancelledEvenWithoutCurrentDebt() {
        val session = TestFixtures.sessionWithActivePlayer(
            TestFixtures.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
                mapOf("USR_01" to 1000, "USR_02" to 50000),
            ),
            "USR_02",
            engine,
        )
        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_02"))
        assertTrue(applied.session.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })

        val cancelled = engine.process(
            applied.session,
            GameCommand.CancelEventPreview("EVT_05", "USR_02"),
        )
        assertEquals(GameOutcome.REJECTED, cancelled.outcome)
    }
}
