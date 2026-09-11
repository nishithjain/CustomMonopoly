package com.boardbanker.core.dice

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.TestFixtures
import com.boardbanker.core.model.EditionIds
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollerTest {
    @Test
    fun randomDiceRollerUsesIndependentCallsForEachDie() {
        val values = mutableListOf(3, 5, 4, 2).iterator()
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = nextInt()
            override fun nextInt(until: Int): Int = nextInt(0, until)
            override fun nextInt(from: Int, until: Int): Int = values.next()
        }
        val roller = RandomDiceRoller(random)

        val first = roller.roll()
        val second = roller.roll()

        assertEquals(3, first.die1)
        assertEquals(5, first.die2)
        assertEquals(4, second.die1)
        assertEquals(2, second.die2)
    }

    @Test
    fun diceValuesStayWithinOneThroughSix() {
        val roller = SequenceDiceRoller(1 to 6, 6 to 1)
        val first = roller.roll()
        val second = roller.roll()
        assertEquals(1, first.die1)
        assertEquals(6, first.die2)
        assertEquals(6, second.die1)
        assertEquals(1, second.die2)
    }

    @Test
    fun fakeRollThreeFiveIsNotDoubles() {
        val result = DiceResult(3, 5)
        assertFalse(result.isDoubles)
    }

    @Test
    fun fakeRollFourFourIsDoubles() {
        val result = DiceResult(4, 4)
        assertTrue(result.isDoubles)
    }

    @Test
    fun engineAppliesConfiguredNonDoublesRoll() {
        val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val engine = DefaultGameEngine(definitions, SequenceDiceRoller(3 to 5))
        var session = TestFixtures.indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
        session = TestFixtures.selectDiceGambleMode(
            session,
            com.boardbanker.core.model.DiceGambleMode.IN_APP,
            engine,
        )
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(GameOutcome.PENDING_ACTION, rolled.outcome)
        assertEquals(listOf(3, 5), rolled.rolledDice)
        assertEquals(listOf(3, 5), rolled.session.pendingDiceGamble!!.lastRollResults)
    }

    @Test
    fun engineAppliesConfiguredDoublesRoll() {
        val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val engine = DefaultGameEngine(definitions, SequenceDiceRoller(4 to 4))
        var session = TestFixtures.indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
        session = TestFixtures.selectDiceGambleMode(
            session,
            com.boardbanker.core.model.DiceGambleMode.IN_APP,
            engine,
        )
        val before = session.players["USR_01"]!!.balance
        val rolled = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(listOf(4, 4), rolled.rolledDice)
        assertEquals(before + 15000, rolled.session.players["USR_01"]!!.balance)
        assertEquals(null, rolled.session.pendingDiceGamble)
    }
}
