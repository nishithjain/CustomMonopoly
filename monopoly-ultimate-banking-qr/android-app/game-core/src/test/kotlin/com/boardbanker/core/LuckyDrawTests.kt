package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LuckyDrawTests {
    private lateinit var engine: DefaultGameEngine
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
    }

    private fun indiaSession(
        balances: Map<String, Int>? = null,
    ) = TestFixtures.newGameForEdition(EditionIds.INDIA, balances = balances)

    private fun startLuckyDraw(session: com.boardbanker.core.model.GameSession = indiaSession()): com.boardbanker.core.model.GameSession {
        val started = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01"))
        assertNotNull(started.session.pendingEventDraw)
        assertEquals(1, started.session.eventChainDepth)
        assertEquals(1, started.session.pendingEventDraw!!.chainDepth)
        assertEquals(3, started.session.pendingEventDraw!!.maximumChainDepth)
        return started.session
    }

    private fun resolveDraw(
        session: com.boardbanker.core.model.GameSession,
        eventId: String,
        actingPlayerId: String = "USR_01",
    ) = engine.process(
        session,
        GameCommand.ResolvePendingEventDraw(eventId, actingPlayerId),
    )

    @Test
    fun evt15CreatesOnePendingDraw() {
        val session = startLuckyDraw()
        assertEquals("EVT_15", session.pendingEventDraw!!.parentEventId)
        assertEquals("USR_01", session.pendingEventDraw!!.actingPlayerId)
    }

    @Test
    fun validEventScanConsumesPendingDrawOnce() {
        val session = startLuckyDraw()
        val resolved = resolveDraw(session, "EVT_03")
        assertNull(resolved.session.pendingEventDraw)
        assertEquals(0, resolved.session.eventChainDepth)
        assertEquals(1, resolved.transactions.count { it.transactionType == TransactionType.EVENT_APPLIED })
    }

    @Test
    fun followUpEventResolvesNormally() {
        val session = startLuckyDraw(TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 10000)))
        val before = session.players["USR_01"]!!.balance
        val resolved = resolveDraw(session, "EVT_03")
        assertTrue(resolved.session.players["USR_01"]!!.balance > before)
    }

    @Test
    fun secondEvt15ContinuesChain() {
        var session = startLuckyDraw()
        session = resolveDraw(session, "EVT_15").session
        assertNotNull(session.pendingEventDraw)
        assertEquals(2, session.eventChainDepth)
        assertEquals(2, session.pendingEventDraw!!.chainDepth)
    }

    @Test
    fun thirdEvt15ReachesConfiguredMaximumDepth() {
        var session = startLuckyDraw()
        session = resolveDraw(session, "EVT_15").session
        session = resolveDraw(session, "EVT_15").session
        assertEquals(3, session.eventChainDepth)
        assertEquals(3, session.pendingEventDraw!!.chainDepth)
    }

    @Test
    fun fourthEvt15InChainRejectedWithoutConsumingPendingDraw() {
        var session = startLuckyDraw()
        session = resolveDraw(session, "EVT_15").session
        session = resolveDraw(session, "EVT_15").session
        val fourth = resolveDraw(session, "EVT_15")
        assertEquals(GameOutcome.REJECTED, fourth.outcome)
        assertNotNull(fourth.session.pendingEventDraw)
        assertEquals(3, fourth.session.eventChainDepth)
    }

    @Test
    fun chainCompletionResetsDepth() {
        val session = startLuckyDraw()
        val resolved = resolveDraw(session, "EVT_11")
        assertNull(resolved.session.pendingEventDraw)
        assertEquals(0, resolved.session.eventChainDepth)
    }

    @Test
    fun laterIndependentEvt15StartsWithCleanDepth() {
        var session = startLuckyDraw()
        session = resolveDraw(session, "EVT_11").session
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        assertEquals(1, session.eventChainDepth)
        assertEquals(1, session.pendingEventDraw!!.chainDepth)
    }

    @Test
    fun invalidPropertyQrRejected() {
        val session = startLuckyDraw()
        val rejected = engine.process(session, GameCommand.ResolvePendingEventDraw("PRP_01", "USR_01"))
        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertNotNull(rejected.session.pendingEventDraw)
    }

    @Test
    fun unknownEventRejected() {
        val session = startLuckyDraw()
        val rejected = resolveDraw(session, "EVT_99")
        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertNotNull(rejected.session.pendingEventDraw)
    }

    @Test
    fun wrongPlayerRejected() {
        val session = startLuckyDraw()
        val rejected = resolveDraw(session, "EVT_03", actingPlayerId = "USR_02")
        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertNotNull(rejected.session.pendingEventDraw)
    }

    @Test
    fun duplicateResolveRejectedAfterConsumption() {
        val session = startLuckyDraw()
        val first = resolveDraw(session, "EVT_11")
        val second = resolveDraw(first.session, "EVT_11")
        assertEquals(GameOutcome.REJECTED, second.outcome)
    }

    @Test
    fun applyEventBlockedWhilePendingDrawExists() {
        val session = startLuckyDraw()
        val blocked = engine.process(session, GameCommand.ApplyEvent("EVT_03", "USR_01"))
        assertEquals(GameOutcome.REJECTED, blocked.outcome)
        assertNotNull(blocked.session.pendingEventDraw)
    }

    @Test
    fun endTurnBlockedWhilePendingDrawExists() {
        val session = startLuckyDraw()
        val result = engine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun saveRestoreBeforeFollowUpScan() {
        val session = startLuckyDraw()
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNotNull(restored.pendingEventDraw)
        assertEquals(1, restored.eventChainDepth)
    }

    @Test
    fun saveRestoreDuringMultiEvt15Chain() {
        var session = startLuckyDraw()
        session = resolveDraw(session, "EVT_15").session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(2, restored.eventChainDepth)
        assertEquals(2, restored.pendingEventDraw!!.chainDepth)
    }

    @Test
    fun saveRestoreAfterValidFollowUpCompletes() {
        val session = resolveDraw(startLuckyDraw(), "EVT_11").session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNull(restored.pendingEventDraw)
        assertEquals(0, restored.eventChainDepth)
    }

    @Test
    fun chainedLuckyBreakCreatesDiceGamble() {
        val session = startLuckyDraw()
        val resolved = resolveDraw(session, "EVT_17")
        assertNull(resolved.session.pendingEventDraw)
        assertNotNull(resolved.session.pendingDiceGamble)
    }

    @Test
    fun ukEditionDoesNotCreatePendingDrawForEvt15() {
        val ukEngine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.UK))
        var session = TestFixtures.newGameForEdition(EditionIds.UK)
        val result = ukEngine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01", propertyId = "PRP_01"))
        assertNull(result.session.pendingEventDraw)
        assertEquals(0, result.session.eventChainDepth)
    }
}
