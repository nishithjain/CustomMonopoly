package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.rules.TurnScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkipTurnTests {
    private lateinit var indiaEngine: DefaultGameEngine
    private lateinit var ukEngine: DefaultGameEngine
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        indiaEngine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
        ukEngine = TestFixtures.engine as DefaultGameEngine
    }

    @Test
    fun evt18_grantsOnePendingSkip() {
        val session = TestFixtures.indiaGame()
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01"))
        assertEquals(1, result.session.players["USR_01"]!!.pendingSkipTurnCount)
    }

    @Test
    fun evt18_nonStackingRemainsOne() {
        var session = TestFixtures.indiaGame()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01")).session
        val second = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01"))
        assertEquals(1, second.session.players["USR_01"]!!.pendingSkipTurnCount)
    }

    @Test
    fun twoPlayer_nextScheduledTurnIsSkipped() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
        assertEquals(0, result.session.players["USR_02"]!!.pendingSkipTurnCount)
        assertTrue(result.skippedTurnPlayerIds == listOf("USR_02"))
    }

    @Test
    fun skipCountConsumedOnce() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals(0, result.session.players["USR_02"]!!.pendingSkipTurnCount)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.TURN_SKIPPED })
    }

    @Test
    fun followingLaterTurnProceedsNormally() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val second = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_02", second.session.turnState!!.activePlayerId)
        assertTrue(second.skippedTurnPlayerIds.isEmpty())
    }

    @Test
    fun threePlayer_consecutiveSkips() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.withPendingSkip(session, "USR_03")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
        assertEquals(listOf("USR_02", "USR_03"), result.skippedTurnPlayerIds)
        assertEquals(2, result.transactions.count { it.transactionType == TransactionType.TURN_SKIPPED })
    }

    @Test
    fun fourPlayer_wraparoundScheduling() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03", "USR_04"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.withPendingSkip(session, "USR_03")
        session = TestFixtures.withPendingSkip(session, "USR_04")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
        assertEquals(listOf("USR_02", "USR_03", "USR_04"), result.skippedTurnPlayerIds)
    }

    @Test
    fun skippedPlayerInJailRemainsJailed() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        val jailed = session.players["USR_02"]!!.copy(jailStatus = true, pendingSkipTurnCount = 1)
        session = session.copy(players = session.players + ("USR_02" to jailed))
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
    }

    @Test
    fun eliminatedBankruptPlayerSkippedInOrder() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        val bankrupt = session.players["USR_02"]!!.copy(bankrupt = true, active = false)
        session = session.copy(players = session.players + ("USR_02" to bankrupt))
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_03", result.session.turnState!!.activePlayerId)
        assertTrue(result.skippedTurnPlayerIds.isEmpty())
    }

    @Test
    fun endTurnDoesNotGrantGoSalary() {
        val session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        val balanceBefore = session.players["USR_01"]!!.balance
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals(balanceBefore, result.session.players["USR_01"]!!.balance)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun oneActivityEntryPerSkippedPlayer() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.withPendingSkip(session, "USR_03")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals(2, result.transactions.count { it.transactionType == TransactionType.TURN_SKIPPED })
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.TURN_ADVANCED })
    }

    @Test
    fun doubleEndTurn_secondRejected() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val second = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameOutcome.REJECTED, second.outcome)
        assertEquals("USR_02", second.session.turnState!!.activePlayerId)
    }

    @Test
    fun saveRestore_beforeConsumption() {
        var session = TestFixtures.indiaGame()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(1, restored.players["USR_01"]!!.pendingSkipTurnCount)
    }

    @Test
    fun saveRestore_afterConsumption() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(0, restored.players["USR_02"]!!.pendingSkipTurnCount)
        assertEquals("USR_01", restored.turnState!!.activePlayerId)
    }

    @Test
    fun saveRestore_multiplePendingSkips() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.withPendingSkip(session, "USR_03")
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(1, restored.players["USR_02"]!!.pendingSkipTurnCount)
        assertEquals(1, restored.players["USR_03"]!!.pendingSkipTurnCount)
    }

    @Test
    fun undo_restoresConsumedSkipsAndActivePlayer() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        session = TestFixtures.withPendingSkip(session, "USR_03")
        val afterEnd = TestFixtures.endTurn(session, engine = indiaEngine).session
        val undone = indiaEngine.process(afterEnd, GameCommand.UndoLastAction).session
        assertEquals("USR_01", undone.turnState!!.activePlayerId)
        assertEquals(1, undone.players["USR_02"]!!.pendingSkipTurnCount)
        assertEquals(1, undone.players["USR_03"]!!.pendingSkipTurnCount)
    }

    @Test
    fun ukEndTurn_advancesWithoutSkip() {
        val session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        val result = TestFixtures.endTurn(session, engine = ukEngine)
        assertEquals("USR_02", result.session.turnState!!.activePlayerId)
        assertTrue(result.skippedTurnPlayerIds.isEmpty())
    }

    @Test
    fun startGame_initializesTurnState() {
        val session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        assertEquals("USR_01", session.turnState!!.activePlayerId)
        assertEquals(listOf("USR_01", "USR_02"), session.turnState!!.turnOrder)
    }

    @Test
    fun schedulerSafetyBoundIsExplicit() {
        assertEquals(16, TurnScheduler.MAX_SCHEDULING_ITERATIONS)
    }

    @Test
    fun activePlayerNeverSetToSkippedCandidateDuringTransition() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_02")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertNotEquals("USR_02", result.session.turnState!!.activePlayerId)
    }

    @Test
    fun ownSkipAppliesOnNextScheduledTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingSkip(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        val second = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals(listOf("USR_01"), second.skippedTurnPlayerIds)
        assertEquals("USR_02", second.session.turnState!!.activePlayerId)
    }
}
