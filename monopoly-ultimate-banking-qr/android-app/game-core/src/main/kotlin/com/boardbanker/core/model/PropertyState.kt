package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PropertyState(
    val propertyId: String,
    val ownerPlayerId: String? = null,
    val currentRentLevel: Int = 1,
)
