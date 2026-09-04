package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuctionState(
    val propertyId: String? = null,
    val energyGridId: String? = null,
    val currentBid: Int = 0,
    val currentBidderId: String? = null,
    val startedByPlayerId: String,
) {
    init {
        require(propertyId != null || energyGridId != null) {
            "Auction must target a property or energy grid"
        }
        require(propertyId == null || energyGridId == null) {
            "Auction cannot target both a property and an energy grid"
        }
    }

    val assetId: String get() = checkNotNull(propertyId ?: energyGridId)
}
