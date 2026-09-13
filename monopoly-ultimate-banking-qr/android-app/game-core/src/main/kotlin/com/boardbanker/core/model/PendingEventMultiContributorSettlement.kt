package com.boardbanker.core.model

import kotlinx.serialization.Serializable

/** Tracks in-progress COLLECT_FROM_EACH_PLAYER event settlement across contributor debts. */
@Serializable
data class PendingEventMultiContributorSettlement(
    val settlementId: String,
    val eventId: String,
    val eventName: String,
    val recipientPlayerId: String,
    val amountPerContributor: Int,
    val contributorPlayerIds: List<String>,
    val completedTransfers: List<EventMultiPlayerTransferSnapshot.Transfer> = emptyList(),
    val skippedContributorIds: List<String> = emptyList(),
) {
    fun remainingContributorIds(): List<String> = contributorPlayerIds.filter { contributorId ->
        completedTransfers.none { it.fromPlayerId == contributorId } &&
            contributorId !in skippedContributorIds
    }

    fun allContributorsResolved(): Boolean = remainingContributorIds().isEmpty()
}
