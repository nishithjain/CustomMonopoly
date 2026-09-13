package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [DebtResolutionState.eventDebt] for multi-recipient event debts. */
@Serializable
data class EventMultiRecipientDebtSnapshot(
    val debtId: String,
    val eventId: String,
    val eventName: String,
    val payerPlayerId: String,
    val direction: EventMultiPlayerTransferSnapshot.Direction,
    val amountPerRecipient: Int,
    val recipientPlayerIds: List<String>,
    val totalAmountDue: Int,
    val cashAvailable: Int,
    val shortfall: Int,
) {
    companion object {
        private const val DEBT_ID = "debtId"
        private const val EVENT_ID = "eventId"
        private const val EVENT_NAME = "eventName"
        private const val PAYER_PLAYER_ID = "payerPlayerId"
        private const val DIRECTION = "direction"
        private const val AMOUNT_PER_RECIPIENT = "amountPerRecipient"
        private const val RECIPIENT_PLAYER_IDS = "recipientPlayerIds"
        private const val TOTAL_AMOUNT_DUE = "totalAmountDue"
        private const val CASH_AVAILABLE = "cashAvailable"
        private const val SHORTFALL = "shortfall"

        fun stateAfter(snapshot: EventMultiRecipientDebtSnapshot): JsonObject = buildJsonObject {
            put(DEBT_ID, snapshot.debtId)
            put(EVENT_ID, snapshot.eventId)
            put(EVENT_NAME, snapshot.eventName)
            put(PAYER_PLAYER_ID, snapshot.payerPlayerId)
            put(DIRECTION, snapshot.direction.name)
            put(AMOUNT_PER_RECIPIENT, snapshot.amountPerRecipient)
            put(RECIPIENT_PLAYER_IDS, snapshot.recipientPlayerIds.joinToString(","))
            put(TOTAL_AMOUNT_DUE, snapshot.totalAmountDue)
            put(CASH_AVAILABLE, snapshot.cashAvailable)
            put(SHORTFALL, snapshot.shortfall)
        }

        fun fromDebtResolution(debt: DebtResolutionState): EventMultiRecipientDebtSnapshot? =
            debt.eventDebt

        fun fromBankruptcyTransaction(tx: Transaction): Metadata? {
            if (tx.transactionType != TransactionType.BANKRUPTCY) return null
            if (tx.stateAfter["contributorDebt"]?.jsonPrimitive?.content == "true") return null
            val eventId = tx.stateAfter[EVENT_ID]?.jsonPrimitive?.content ?: tx.eventId ?: return null
            val eventName = tx.stateAfter[EVENT_NAME]?.jsonPrimitive?.content ?: return null
            return Metadata(eventId = eventId, eventName = eventName)
        }

        data class Metadata(
            val eventId: String,
            val eventName: String,
        )

        fun bankruptcyStateAfter(eventId: String, eventName: String): JsonObject = buildJsonObject {
            put(EVENT_ID, eventId)
            put(EVENT_NAME, eventName)
        }
    }
}
