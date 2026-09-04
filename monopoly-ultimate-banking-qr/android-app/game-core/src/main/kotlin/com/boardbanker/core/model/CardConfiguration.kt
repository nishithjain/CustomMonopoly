package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CardConfiguration(
    val playerCardCount: Int,
    val propertyCardCount: Int,
    val eventCardCount: Int,
    val rentLevelsPerProperty: Int,
    val energyGridCardCount: Int = 0,
)
