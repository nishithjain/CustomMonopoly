package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PurchaseAssetType {
    PROPERTY,
    ENERGY_GRID,
}
