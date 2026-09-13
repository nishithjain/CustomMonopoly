package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [DebtResolutionState.eventBankDebit] for bank-payment event debts. */
@Serializable
data class EventBankDebitDebtSnapshot(
    val debtId: String,
    val obligationId: String,
    val eventId: String,
    val eventName: String,
    val debtorPlayerId: String,
    val totalAmountDue: Int,
    val cashAvailable: Int,
    val shortfall: Int,
) {
    companion object {
        private const val DEBT_ID = "debtId"
        private const val OBLIGATION_ID = "obligationId"
        private const val EVENT_ID = "eventId"
        private const val EVENT_NAME = "eventName"
        private const val DEBTOR_PLAYER_ID = "debtorPlayerId"
        private const val TOTAL_AMOUNT_DUE = "totalAmountDue"
        private const val CASH_AVAILABLE = "cashAvailable"
        private const val SHORTFALL = "shortfall"

        fun stateAfter(snapshot: EventBankDebitDebtSnapshot): JsonObject = buildJsonObject {
            put(DEBT_ID, snapshot.debtId)
            put(OBLIGATION_ID, snapshot.obligationId)
            put(EVENT_ID, snapshot.eventId)
            put(EVENT_NAME, snapshot.eventName)
            put(DEBTOR_PLAYER_ID, snapshot.debtorPlayerId)
            put(TOTAL_AMOUNT_DUE, snapshot.totalAmountDue)
            put(CASH_AVAILABLE, snapshot.cashAvailable)
            put(SHORTFALL, snapshot.shortfall)
        }

        fun bankruptcyStateAfter(eventId: String, eventName: String): JsonObject = buildJsonObject {
            put(EVENT_ID, eventId)
            put(EVENT_NAME, eventName)
        }

        fun fromBankruptcyTransaction(tx: Transaction): Metadata? {
            if (tx.transactionType != TransactionType.BANKRUPTCY) return null
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
