package com.boardbanker.core.card

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    USER,
    PROPERTY,
    EVENT,
    ENERGY_GRID,
}
