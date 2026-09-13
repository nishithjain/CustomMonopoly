package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventObligationStatus {
    PENDING,
    AWAITING_FUNDS,
    PAID,
    BANKRUPT,
}

@Serializable
data class EventObligation(
    val obligationId: String,
    val payerId: String,
    val recipientId: String,
    val amount: Int,
    val status: EventObligationStatus,
    val cashPaid: Int = 0,
)
