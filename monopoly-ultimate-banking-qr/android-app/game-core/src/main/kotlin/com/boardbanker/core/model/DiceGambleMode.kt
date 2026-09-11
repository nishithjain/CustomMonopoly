package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiceGambleMode {
    IN_APP,
    PHYSICAL,
}

@Serializable
enum class PhysicalDiceGambleOutcome {
    JACKPOT,
    PENALTY,
}

@Serializable
enum class LuckyBreakOutcome {
    JACKPOT,
    PENALTY,
}
