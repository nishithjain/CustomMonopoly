package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuckyBreakTests {
    private val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    private fun engineWithRolls(vararg rolls: Pair<Int, Int>): DefaultGameEngine =
        DefaultGameEngine(definitions, SequenceDiceRoller(*rolls))

    private fun startLuckyBreak(
        engine: DefaultGameEngine,
        session: com.boardbanker.core.model.GameSession = TestFixtures.indiaGame(),
    ): com.boardbanker.core.model.GameSession {
        val started = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01"))
        assertNotNull(started.session.pendingDiceGamble)
        return started.session
    }

    @Test
    fun firstAttemptDoublesCreditsJackpot() {
        val engine = engineWithRolls(4 to 4)
        val session = startLuckyBreak(engine)
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
        assertEquals(1, rolled.transactions.count { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test
    fun secondAttemptDoublesCreditsJackpot() {
        val engine = engineWithRolls(1 to 2, 5 to 5)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun thirdAttemptDoublesCreditsJackpot() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 6 to 6)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun threeFailuresDebitPenalty() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 4 to 5)
        var session = startLuckyBreak(
            engine,
            TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 50000)),
        )
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(45000, rolled.session.players["USR_01"]!!.balance)
        assertNull(rolled.session.pendingDiceGamble)
        assertEquals(1, rolled.transactions.count { it.transactionType == TransactionType.BANK_DEBIT })
    }

    @Test
    fun attemptCountIncrementsAfterFailedRoll() {
        val engine = engineWithRolls(1 to 2)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        assertEquals(1, session.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(1, 2), session.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun noFourthRollAfterThreeFailures() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 4 to 5, 1 to 1)
        var session = startLuckyBreak(
            engine,
            TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 50000)),
        )
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val fourth = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(GameOutcome.REJECTED, fourth.outcome)
    }

    @Test
    fun noExtraTurnGrantedOnDoubles() {
        val engine = engineWithRolls(2 to 2)
        val session = startLuckyBreak(engine)
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertFalse(rolled.session.players["USR_01"]!!.pendingExtraTurn)
    }

    @Test
    fun noPhysicalMovementOnResolution() {
        val engine = engineWithRolls(3 to 3)
        val session = startLuckyBreak(engine)
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertTrue(rolled.physicalActions.isEmpty())
    }

    @Test
    fun penaltyTriggersDebtFlowWhenInsufficientFunds() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 4 to 5)
        var session = startLuckyBreak(
            engine,
            TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 1000)),
        )
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, rolled.outcome)
        assertNotNull(rolled.session.debtResolution)
        assertNull(rolled.session.pendingDiceGamble)
    }

    @Test
    fun doubleRollCommandRejectedAfterSuccess() {
        val engine = engineWithRolls(1 to 1, 2 to 2)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val second = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(GameOutcome.REJECTED, second.outcome)
    }

    @Test
    fun endTurnBlockedWhileGamblePending() {
        val engine = engineWithRolls()
        val session = startLuckyBreak(engine)
        val result = engine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun saveRestoreBeforeFirstRoll() {
        val engine = engineWithRolls()
        val session = startLuckyBreak(engine)
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNotNull(restored.pendingDiceGamble)
        assertEquals(0, restored.pendingDiceGamble!!.attemptsUsed)
    }

    @Test
    fun saveRestoreAfterOneFailedRoll() {
        val engine = engineWithRolls(1 to 3)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(1, restored.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(1, 3), restored.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun saveRestoreAfterTwoFailedRolls() {
        val engine = engineWithRolls(1 to 2, 3 to 4)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(2, restored.pendingDiceGamble!!.attemptsUsed)
        assertEquals(listOf(3, 4), restored.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun saveRestoreAfterSuccessDoesNotReopenGamble() {
        val engine = engineWithRolls(2 to 2)
        var session = startLuckyBreak(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNull(restored.pendingDiceGamble)
    }

    @Test
    fun saveRestoreDuringPenaltyDebt() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 4 to 5)
        var session = startLuckyBreak(
            engine,
            TestFixtures.newGameForEdition(EditionIds.INDIA, balances = mapOf("USR_01" to 1000)),
        )
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertNull(restored.pendingDiceGamble)
        assertNotNull(restored.debtResolution)
    }

    @Test
    fun configuredJackpotAndPenaltyAmounts() {
        val engine = engineWithRolls()
        val session = startLuckyBreak(engine)
        assertEquals(15000, session.pendingDiceGamble!!.jackpotAmount)
        assertEquals(5000, session.pendingDiceGamble!!.penaltyAmount)
        assertEquals(3, session.pendingDiceGamble!!.maximumAttempts)
        assertEquals(2, session.pendingDiceGamble!!.diceCount)
    }
}
