package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [DebtResolutionState.eventContributorDebt] for Birthday-style contributions. */
@Serializable
data class EventContributorDebtSnapshot(
    val debtId: String,
    val settlementId: String,
    val eventId: String,
    val eventName: String,
    val recipientPlayerId: String,
    val contributorPlayerId: String,
    val contributionAmount: Int,
    val cashAvailable: Int,
    val shortfall: Int,
) {
    companion object {
        private const val EVENT_ID = "eventId"
        private const val EVENT_NAME = "eventName"

        fun bankruptcyStateAfter(eventId: String, eventName: String): JsonObject = buildJsonObject {
            put(EVENT_ID, eventId)
            put(EVENT_NAME, eventName)
            put("contributorDebt", true)
        }

        fun fromBankruptcyTransaction(tx: Transaction): Metadata? {
            if (tx.transactionType != TransactionType.BANKRUPTCY) return null
            val contributorDebt = tx.stateAfter["contributorDebt"]?.jsonPrimitive?.content == "true"
            if (!contributorDebt) return null
            val eventId = tx.stateAfter[EVENT_ID]?.jsonPrimitive?.content ?: tx.eventId ?: return null
            val eventName = tx.stateAfter[EVENT_NAME]?.jsonPrimitive?.content ?: return null
            return Metadata(eventId = eventId, eventName = eventName)
        }

        data class Metadata(
            val eventId: String,
            val eventName: String,
        )
    }
}
