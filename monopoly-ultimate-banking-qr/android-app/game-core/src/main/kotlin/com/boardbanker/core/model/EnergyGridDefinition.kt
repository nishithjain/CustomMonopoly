package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EnergyGridRentLevel(
    val ownedCount: Int,
    val amount: Int,
)

@Serializable
data class EnergyGridDefinition(
    val energyGridId: String,
    val name: String,
    val sequence: Int,
    val qrPayload: String,
    val frontAsset: String,
    val qrAsset: String,
    val purchasePrice: Int,
    val rentLevels: List<EnergyGridRentLevel>,
)

@Serializable
data class EnergyGridState(
    val energyGridId: String,
    val ownerPlayerId: String? = null,
)
