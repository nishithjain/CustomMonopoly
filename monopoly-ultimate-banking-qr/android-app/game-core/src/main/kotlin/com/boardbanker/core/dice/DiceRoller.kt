package com.boardbanker.core.dice

import kotlin.random.Random

data class DiceResult(
    val die1: Int,
    val die2: Int,
) {
    init {
        require(die1 in DiceValues.MIN..DiceValues.MAX) { "die1 must be between 1 and 6" }
        require(die2 in DiceValues.MIN..DiceValues.MAX) { "die2 must be between 1 and 6" }
    }

    fun asList(): List<Int> = listOf(die1, die2)

    val isDoubles: Boolean get() = die1 == die2
}

fun interface DiceRoller {
    fun roll(): DiceResult
}

class RandomDiceRoller(
    private val random: Random = Random.Default,
) : DiceRoller {
    override fun roll(): DiceResult =
        DiceResult(
            die1 = random.nextInt(DiceValues.MIN, DiceValues.MAX + 1),
            die2 = random.nextInt(DiceValues.MIN, DiceValues.MAX + 1),
        )
}

class SequenceDiceRoller(
    private val rolls: Iterator<DiceResult>,
) : DiceRoller {
    constructor(vararg diePairs: Pair<Int, Int>) : this(
        diePairs.map { (die1, die2) -> DiceResult(die1, die2) }.iterator(),
    )

    override fun roll(): DiceResult = rolls.next()
}

object DiceValues {
    const val MIN = 1
    const val MAX = 6
}
