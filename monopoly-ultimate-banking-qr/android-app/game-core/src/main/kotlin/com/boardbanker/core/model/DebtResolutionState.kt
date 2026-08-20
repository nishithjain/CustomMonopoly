package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DebtReason {
    RENT,
    JAIL,
    PURCHASE,
    LOCATION,
    GENERIC,
}

@Serializable
data class DebtResolutionState(
    val debtorPlayerId: String,
    val creditorPlayerId: String,
    val amountRemaining: Int,
    val reason: DebtReason = DebtReason.GENERIC,
    val propertyId: String? = null,
)
