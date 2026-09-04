package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingEnergyGridLanding(
    val actingPlayerId: String,
    val energyGridId: String,
    val sourceEventId: String? = null,
)
