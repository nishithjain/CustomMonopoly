package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Transaction(
    val transactionId: String,
    val gameId: String,
    val timestamp: Long,
    val transactionType: TransactionType,
    val fromEntity: String? = null,
    val toEntity: String? = null,
    val playerId: String? = null,
    val propertyId: String? = null,
    val eventId: String? = null,
    val amount: Int? = null,
    val stateBefore: JsonObject = JsonObject(emptyMap()),
    val stateAfter: JsonObject = JsonObject(emptyMap()),
    val reversible: Boolean = false,
)
