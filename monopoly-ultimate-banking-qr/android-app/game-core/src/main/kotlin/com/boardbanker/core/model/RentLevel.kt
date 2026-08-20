package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RentLevel(
    val level: Int,
    val amount: Int,
)
