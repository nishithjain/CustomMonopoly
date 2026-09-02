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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LuckyBreakTests {
    private lateinit var engine: DefaultGameEngine
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        engine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
    }

    private fun startLuckyBreak(session: com.boardbanker.core.model.GameSession = TestFixtures.indiaGame()): com.boardbanker.core.model.GameSession {
        val started = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01"))
        assertNotNull(started.session.pendingDiceGamble)
        return started.session
    }

    @Test
    fun firstAttemptDoublesCreditsJackpot() {
        val session = startLuckyBreak()
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 4)))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
        assertEquals(1, rolled.transactions.count { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun secondAttemptDoublesCreditsJackpot() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(5, 5)))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun thirdAttemptDoublesCreditsJackpot() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(6, 6)))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun threeFailuresDebitPenalty() {
        var session = startLuckyBreak(TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 50000)))
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5)))
        assertEquals(45000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
        assertEquals(1, rolled.transactions.count { it.transactionType == TransactionType.BANK_DEBIT })
    }

    @Test
    fun attemptCountIncrementsAfterFailedRoll() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        assertEquals(1, session.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(1, 2), session.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun noFourthRollAfterThreeFailures() {
        var session = startLuckyBreak(TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 50000)))
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5))).session
        val fourth = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 1)))
        assertEquals(GameOutcome.REJECTED, fourth.outcome)
    }

    @Test
    fun noExtraTurnGrantedOnDoubles() {
        val session = startLuckyBreak()
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 2)))
        assertFalse(rolled.session.players["USR_01"]!!.pendingExtraTurn)
    }

    @Test
    fun noPhysicalMovementOnResolution() {
        val session = startLuckyBreak()
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(3, 3)))
        assertTrue(rolled.physicalActions.isEmpty())
    }

    @Test
    fun penaltyTriggersDebtFlowWhenInsufficientFunds() {
        var session = startLuckyBreak(TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 1000)))
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5)))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, rolled.outcome)
        assertNotNull(rolled.session.debtResolution)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun doubleRollCommandRejectedAfterSuccess() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 1))).session
        val second = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 2)))
        assertEquals(GameOutcome.REJECTED, second.outcome)
    }

    @Test
    fun endTurnBlockedWhileGamblePending() {
        val session = startLuckyBreak()
        val result = engine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun saveRestoreBeforeFirstRoll() {
        val session = startLuckyBreak()
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNotNull(restored.pendingDiceGamble)
        assertEquals(0, restored.pendingDiceGamble!!.attemptsUsed)
    }

    @Test
    fun saveRestoreAfterOneFailedRoll() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 3))).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(1, restored.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(1, 3), restored.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun saveRestoreAfterTwoFailedRolls() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(3, 4))).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(2, restored.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(3, 4), restored.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun saveRestoreAfterSuccessDoesNotReopenGamble() {
        var session = startLuckyBreak()
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 2))).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNull(restored.pendingDiceGamble)
    }

    @Test
    fun saveRestoreDuringPenaltyDebt() {
        var session = startLuckyBreak(TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 1000)))
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5))).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNull(restored.pendingDiceGamble)
        assertNotNull(restored.debtResolution)
    }

    @Test
    fun invalidDiceCountRejected() {
        val session = startLuckyBreak()
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(3)))
        assertEquals(GameOutcome.REJECTED, rolled.outcome)
    }

    @Test
    fun configuredJackpotAndPenaltyAmounts() {
        val session = startLuckyBreak()
        assertEquals(15000, session.pendingDiceGamble!!.jackpotAmount)
        assertEquals(5000, session.pendingDiceGamble!!.penaltyAmount)
        assertEquals(3, session.pendingDiceGamble!!.maximumAttempts)
        assertEquals(2, session.pendingDiceGamble!!.diceCount)
    }
}
