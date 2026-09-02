package com.boardbanker.core.dice

import kotlin.random.Random

fun interface DiceRoller {
    fun roll(count: Int): List<Int>
}

class RandomDiceRoller(
    private val random: Random = Random.Default,
) : DiceRoller {
    override fun roll(count: Int): List<Int> {
        require(count > 0) { "Dice count must be positive" }
        return List(count) { random.nextInt(DiceValues.MIN, DiceValues.MAX + 1) }
    }
}

class SequenceDiceRoller(
    private val rolls: Iterator<List<Int>>,
) : DiceRoller {
    override fun roll(count: Int): List<Int> {
        val next = rolls.next()
        require(next.size == count) { "Expected $count dice values but received ${next.size}" }
        return next
    }
}

object DiceValues {
    const val MIN = 1
    const val MAX = 6
}
