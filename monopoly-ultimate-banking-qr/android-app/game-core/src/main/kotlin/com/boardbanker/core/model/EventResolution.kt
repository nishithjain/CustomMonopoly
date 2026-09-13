package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventResolutionPhase {
    IN_PROGRESS,
    AWAITING_DEBT,
    COMPLETE,
}

@Serializable
data class EventResolution(
    val resolutionId: String,
    val eventId: String,
    val actingPlayerId: String,
    val obligations: List<EventObligation>,
    val phase: EventResolutionPhase,
    val bankingRecorded: Boolean = false,
) {
    fun obligation(obligationId: String): EventObligation? =
        obligations.firstOrNull { it.obligationId == obligationId }

    fun withObligation(updated: EventObligation): EventResolution = copy(
        obligations = obligations.map { if (it.obligationId == updated.obligationId) updated else it },
    )

    fun isComplete(): Boolean = phase == EventResolutionPhase.COMPLETE

    fun isBankingRecordedFor(eventId: String, actingPlayerId: String): Boolean =
        this.eventId == eventId &&
            this.actingPlayerId == actingPlayerId &&
            bankingRecorded

    fun bankDebitObligationPaid(): Boolean =
        obligations.any { it.recipientId == EntityRef.BANK && it.status == EventObligationStatus.PAID }

    fun contributorObligationPaid(contributorId: String): Boolean =
        obligations.any { it.payerId == contributorId && it.status == EventObligationStatus.PAID }
}
