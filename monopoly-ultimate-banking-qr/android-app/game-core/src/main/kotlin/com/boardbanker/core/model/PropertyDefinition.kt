package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PropertyDefinition(
    val propertyId: String,
    val name: String,
    val qrPayload: String,
    val colorGroup: String,
    val purchasePrice: Int,
    val initialRentLevel: Int,
    val rentLevels: List<RentLevel>,
    val maximumRentLevel: Int = 5,
)
