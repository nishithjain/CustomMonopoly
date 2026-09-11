package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.DirectJailEventSnapshot
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrafficCourtTests {
    private lateinit var engine: DefaultGameEngine

    @Before
    fun setUp() {
        engine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
    }

    @Test
    fun trafficCourt_jailsActivePlayerWithoutGoCreditOrBalanceChange() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        val balanceBefore = session.players["USR_02"]!!.balance

        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))

        assertTrue(result.isSuccess)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertEquals(balanceBefore, result.session.players["USR_02"]!!.balance)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun trafficCourt_endsTurnAndAdvancesToNextEligiblePlayer() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)

        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))

        assertTrue(result.isSuccess)
        assertEquals("USR_03", result.session.turnState!!.activePlayerId)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.TURN_ADVANCED })
        assertNull(result.session.pendingEventExecution)
        assertNull(result.session.pendingEventDraw)
    }

    @Test
    fun trafficCourt_worksThroughLuckyDraw() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_02")).session
        assertNotNull(session.pendingEventDraw)

        val result = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_12", "USR_02"))

        assertTrue(result.isSuccess)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
        assertNull(result.session.pendingEventDraw)
    }

    @Test
    fun trafficCourt_undoRestoresJailBalanceAndTurnState() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        val balanceBefore = session.players["USR_02"]!!.balance

        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val undone = engine.process(applied.session, GameCommand.UndoLastAction)

        assertTrue(undone.isSuccess)
        assertFalse(undone.session.players["USR_02"]!!.jailStatus)
        assertEquals(balanceBefore, undone.session.players["USR_02"]!!.balance)
        assertEquals("USR_02", undone.session.turnState!!.activePlayerId)
    }

    @Test
    fun trafficCourt_turnAdvanceTransactionTargetsNextPlayer() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val advance = result.transactions.last { it.transactionType == TransactionType.TURN_ADVANCED }
        assertEquals("USR_02", advance.fromEntity)
        assertEquals("USR_01", advance.toEntity)
        assertEquals("USR_01", advance.playerId)
    }

    @Test
    fun trafficCourt_recordsJailAndTurnAdvanceInOneActionGroup() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val actionTimestamps = result.transactions
            .filter {
                it.transactionType == TransactionType.JAIL_STATUS_CHANGE ||
                    it.transactionType == TransactionType.TURN_ADVANCED ||
                    it.transactionType == TransactionType.EVENT_APPLIED
            }
            .map { it.timestamp }
            .distinct()
        assertEquals(1, actionTimestamps.size)
    }

    @Test
    fun trafficCourt_recordsDirectJailMetadataOnEventApplied() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)

        val result = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val eventApplied = result.transactions.single { it.transactionType == TransactionType.EVENT_APPLIED }
        val metadata = DirectJailEventSnapshot.fromEventApplied(eventApplied)

        assertNotNull(metadata)
        assertEquals("USR_02", metadata!!.affectedPlayerId)
        assertEquals(EntityRef.JAIL, metadata.destination)
        assertFalse(metadata.collectedGo)
        assertTrue(metadata.turnEnded)
        assertEquals("EVT_12", eventApplied.eventId)
    }

    @Test
    fun trafficCourt_luckyDrawRecordsDirectJailMetadata() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        session = engine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_02")).session

        val result = engine.process(session, GameCommand.ResolvePendingEventDraw("EVT_12", "USR_02"))
        val eventApplied = result.transactions.single { it.transactionType == TransactionType.EVENT_APPLIED }
        val metadata = DirectJailEventSnapshot.fromEventApplied(eventApplied)

        assertNotNull(metadata)
        assertEquals("USR_02", metadata!!.affectedPlayerId)
        assertFalse(metadata.collectedGo)
        assertTrue(metadata.turnEnded)
    }

    @Test
    fun trafficCourt_undoAndResumePreserveDirectJailMetadata() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine = engine)
        val serializer = KotlinGameSessionSerializer()

        val applied = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val resumed = serializer.deserialize(serializer.serialize(applied.session))
        val metadataBeforeUndo = DirectJailEventSnapshot.fromEventApplied(
            resumed.transactions.single { it.transactionType == TransactionType.EVENT_APPLIED },
        )
        assertEquals("USR_02", metadataBeforeUndo!!.affectedPlayerId)

        val undone = engine.process(resumed, GameCommand.UndoLastAction)
        val restored = engine.process(undone.session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val metadataAfterRedo = DirectJailEventSnapshot.fromEventApplied(
            restored.transactions.last { it.transactionType == TransactionType.EVENT_APPLIED },
        )

        assertEquals("USR_02", metadataAfterRedo!!.affectedPlayerId)
        assertTrue(metadataAfterRedo.turnEnded)
    }

    @Test
    fun trafficCourt_rejectsDuplicateApplicationWhenAlreadyInJail() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_01")).session
        session = TestFixtures.endTurn(session, engine = engine).session
        assertEquals("USR_01", session.turnState!!.activePlayerId)
        assertTrue(session.players["USR_01"]!!.jailStatus)

        val duplicate = engine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_01"))

        assertFalse(duplicate.isSuccess)
        assertTrue(duplicate.session.players["USR_01"]!!.jailStatus)
        assertEquals("USR_01", duplicate.session.turnState!!.activePlayerId)
    }
}
