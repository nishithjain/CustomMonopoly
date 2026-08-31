package com.boardbanker.core.event

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventTargetType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.PendingEventExecution
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiActionEventTests {
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun ukSingleActionEventsRemainUnchanged() {
        val engine = DefaultGameEngine(TestFixtures.definitions)
        val session = TestFixtures.newGame()
        val beforeBalance = session.players["USR_01"]!!.balance

        val result = engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_13",
                actingPlayerId = "USR_01",
            ),
        )

        assertTrue(result.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED })
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EVENT_APPLIED })
        assertNull(result.session.pendingEventExecution)
        assertEquals(beforeBalance, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun threeImmediateActionsExecuteInOrder() {
        val definitions = definitionsWithEvent(
            "EVT_MULTI_IMM",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("TOTAL_GRIDLOCK_V1"),
                action("PAY_PER_OWNED_PROPERTY"),
            ),
        )
        val engine = DefaultGameEngine(definitions)
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 1)
        val beforeBalance = session.players["USR_01"]!!.balance

        val result = engine.process(
            session,
            GameCommand.ApplyEvent(eventId = "EVT_MULTI_IMM", actingPlayerId = "USR_01"),
        )

        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EVENT_APPLIED })
        assertTrue(result.session.temporaryEffects.isNotEmpty())
        assertEquals(beforeBalance - 50, result.session.players["USR_01"]!!.balance)
        assertNull(result.session.pendingEventExecution)
    }

    @Test
    fun immediateInteractiveImmediateSequenceResumesCorrectly() {
        val definitions = definitionsWithEvent(
            "EVT_MULTI_SCAN",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
                action("PAY_PER_OWNED_PROPERTY"),
            ),
        )
        val engine = DefaultGameEngine(definitions)
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 1)
        val afterCap = engine.process(
            session,
            GameCommand.ApplyEvent(eventId = "EVT_MULTI_SCAN", actingPlayerId = "USR_01"),
        )
        assertNotNull(afterCap.session.pendingEventExecution)
        assertEquals(1, afterCap.session.pendingEventExecution!!.currentActionIndex)
        assertEquals("Second player required", afterCap.pendingMessage)

        val afterCredit = engine.process(
            afterCap.session,
            GameCommand.ApplyEvent(
                eventId = "EVT_MULTI_SCAN",
                actingPlayerId = "USR_01",
                targetPlayerId = "USR_02",
            ),
        )
        assertNull(afterCredit.session.pendingEventExecution)
        assertEquals(1, afterCredit.transactions.count { it.transactionType == TransactionType.EVENT_APPLIED })
        assertTrue(afterCredit.session.temporaryEffects.isNotEmpty())
        assertTrue(afterCredit.session.players["USR_02"]!!.balance > TestFixtures.definitions.bankingValues.startingBalance)
    }

    @Test
    fun pendingMultiActionEventSurvivesSaveAndRestore() {
        val definitions = definitionsWithEvent(
            "EVT_MULTI_RESTORE",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
            ),
        )
        val engine = DefaultGameEngine(definitions)
        val pendingSession = engine.process(
            TestFixtures.newGame(),
            GameCommand.ApplyEvent(eventId = "EVT_MULTI_RESTORE", actingPlayerId = "USR_01"),
        ).session
        assertNotNull(pendingSession.pendingEventExecution)

        val restored = serializer.deserialize(serializer.serialize(pendingSession))
        assertEquals(pendingSession.pendingEventExecution, restored.pendingEventExecution)

        val resumed = engine.process(
            restored,
            GameCommand.ApplyEvent(
                eventId = "EVT_MULTI_RESTORE",
                actingPlayerId = "USR_01",
                targetPlayerId = "USR_02",
            ),
        )
        assertNull(resumed.session.pendingEventExecution)
        assertEquals(1, resumed.transactions.count { it.transactionType == TransactionType.EVENT_APPLIED })
    }

    @Test
    fun completedActionsAreNotRepeatedAfterRestore() {
        val definitions = definitionsWithEvent(
            "EVT_MULTI_NO_REPLAY",
            listOf(
                action("TEMPORARY_RENT_CAP"),
                action("CREDIT_BOTH_PLAYERS", requiresPlayerScan = true, targetType = EventTargetType.TWO_PLAYERS.name),
            ),
        )
        val engine = DefaultGameEngine(definitions)
        val pending = engine.process(
            TestFixtures.newGame(),
            GameCommand.ApplyEvent(eventId = "EVT_MULTI_NO_REPLAY", actingPlayerId = "USR_01"),
        )
        val effectCountBeforeResume = pending.session.temporaryEffects.size
        val balanceBeforeResume = pending.session.players["USR_01"]!!.balance

        val restored = serializer.deserialize(serializer.serialize(pending.session))
        val resumed = engine.process(
            restored,
            GameCommand.ApplyEvent(
                eventId = "EVT_MULTI_NO_REPLAY",
                actingPlayerId = "USR_01",
                targetPlayerId = "USR_02",
            ),
        )

        assertEquals(effectCountBeforeResume, resumed.session.temporaryEffects.size)
        assertEquals(balanceBeforeResume + 200, resumed.session.players["USR_01"]!!.balance)
    }

    private fun definitionsWithEvent(eventId: String, actions: List<EventActionDefinition>): GameDefinitions {
        val uk = TestFixtures.definitions
        val template = uk.events["EVT_13"]!!
        return uk.copy(
            events = uk.events + (
                eventId to EventDefinition(
                    eventId = eventId,
                    deckId = template.deckId,
                    name = "Multi Action Test",
                    qrPayload = "MUB:TEST:MULTI",
                    eventSubtitle = template.eventSubtitle,
                    eventDescription = template.eventDescription,
                    actions = actions,
                )
            ),
        )
    }

    private fun action(
        actionType: String,
        requiresPlayerScan: Boolean = false,
        targetType: String = EventTargetType.NONE.name,
    ): EventActionDefinition = EventActionDefinition(
        actionType = actionType,
        targetType = targetType,
        requiresPlayerScan = requiresPlayerScan,
        requiresPropertyScan = false,
    )
}
