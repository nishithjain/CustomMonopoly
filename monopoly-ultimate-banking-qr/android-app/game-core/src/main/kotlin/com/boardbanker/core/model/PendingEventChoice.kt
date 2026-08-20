package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingEventChoice(
    val eventId: String,
    val actingPlayerId: String,
    val propertyId: String,
)
