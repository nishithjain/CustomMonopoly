package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cross-workflow integration checks for India edition special mechanics.
 */
class IndiaSpecialWorkflowIntegrationTest {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    @Test
    fun jailPass_saveRestoreAndConsume() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01")).session
        session = TestFixtures.sessionWithJailAndPass("USR_01", passCount = session.players["USR_01"]!!.jailPassCount)
        val restored = serializer.deserialize(serializer.serialize(session))
        val used = engine.process(restored, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertFalse(used.session.players["USR_01"]!!.jailStatus)
        assertEquals(0, used.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun skipNextTurn_cancelsPendingExtraTurnAtBoundary() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        val result = TestFixtures.endTurn(session, engine = engine)
        assertEquals("USR_02", result.session.turnState!!.activePlayerId)
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(0, result.session.players["USR_01"]!!.pendingSkipTurnCount)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP })
    }

    @Test
    fun jailDuringPendingExtraTurn_cancelsExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val jailed = engine.process(session, GameCommand.SendPlayerToJail("USR_01"))
        assertFalse(jailed.session.players["USR_01"]!!.pendingExtraTurn)
        assertTrue(jailed.session.players["USR_01"]!!.jailStatus)
        assertTrue(
            jailed.transactions.any { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL },
        )
    }

    @Test
    fun luckyBreak_successAndFailurePaths() {
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            balances = mapOf("USR_01" to 50000, "USR_02" to 150000),
        )
        session = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
        val success = engine.process(
            session,
            GameCommand.RollEventDice("EVT_17", "USR_01", listOf(6, 6)),
        )
        assertNull(success.session.pendingDiceGamble)
        assertTrue(success.session.players["USR_01"]!!.balance > 50000)

        session = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
        val failure = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2)))
        session = failure.session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        val completed = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5)))
        assertNull(completed.session.pendingDiceGamble)
        assertTrue(completed.session.players["USR_01"]!!.balance < 50000)
    }

    @Test
    fun luckyDraw_chainToLuckyBreak_thenNormalEvent() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        assertNotNull(session.pendingEventDraw)

        val luckyBreak = engine.process(
            session,
            GameCommand.ResolvePendingEventDraw("EVT_17", "USR_01"),
        )
        assertNull(luckyBreak.session.pendingEventDraw)
        assertNotNull(luckyBreak.session.pendingDiceGamble)

        val rolled = engine.process(
            luckyBreak.session,
            GameCommand.RollEventDice("EVT_17", "USR_01", listOf(3, 3)),
        )
        assertNull(rolled.session.pendingDiceGamble)

        session = engine.process(rolled.session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val normal = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_03", "USR_01"))
        assertNull(normal.session.pendingEventDraw)
        assertTrue(normal.transactions.any { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun luckyDraw_nestedChainRespectsMaximumDepth() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        session = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_15", "USR_01")).session
        session = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_15", "USR_01")).session
        val rejectedFourth = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_15", "USR_01"))
        assertEquals(GameOutcome.REJECTED, rejectedFourth.outcome)
        assertNotNull(rejectedFourth.session.pendingEventDraw)
        val completed = engine.process(rejectedFourth.session, GameCommand.ResolvePendingEventDraw("EVT_03", "USR_01"))
        assertNull(completed.session.pendingEventDraw)
        assertEquals(0, completed.session.eventChainDepth)
    }

    @Test
    fun saveRestore_preservesPendingLuckyDrawAndExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        session = TestFixtures.withPendingExtra(session, "USR_02")
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNotNull(restored.pendingEventDraw)
        assertTrue(restored.players["USR_02"]!!.pendingExtraTurn)
    }

    @Test
    fun extraTurn_completesAsNormalTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals(TurnKind.EXTRA, session.turnState!!.turnKind)
        val ended = TestFixtures.endTurn(session, engine = engine)
        assertEquals("USR_02", ended.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.NORMAL, ended.session.turnState!!.turnKind)
    }
}
