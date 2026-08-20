package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuctionState(
    val propertyId: String,
    val currentBid: Int = 0,
    val currentBidderId: String? = null,
    val startedByPlayerId: String,
)
