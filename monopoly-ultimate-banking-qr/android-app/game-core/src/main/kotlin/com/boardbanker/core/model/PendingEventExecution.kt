package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingEventExecution(
    val eventId: String,
    val actingPlayerId: String,
    val currentActionIndex: Int,
    val propertyId: String? = null,
    val targetPlayerId: String? = null,
    val secondPropertyId: String? = null,
    val secondPlayerId: String? = null,
)
