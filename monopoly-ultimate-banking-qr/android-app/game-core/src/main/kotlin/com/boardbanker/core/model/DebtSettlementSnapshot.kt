package com.boardbanker.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [TransactionType.RENT_DEBT_SETTLED] for Recent Banking. */
object DebtSettlementSnapshot {
    private const val SETTLEMENT_ID = "settlementId"
    private const val DEBTOR_PLAYER_ID = "debtorPlayerId"
    private const val CREDITOR_PLAYER_ID = "creditorPlayerId"
    private const val ORIGINAL_AMOUNT_DUE = "originalAmountDue"
    private const val CASH_AMOUNT_USED = "cashAmountUsed"
    private const val PROPERTY_VALUE_USED = "propertyValueUsed"
    private const val REMAINING_DUE = "remainingDue"
    private const val REASON = "reason"
    private const val PROPERTY_ACTIONS = "propertyActions"
    private const val SETTLEMENT_METHOD = "settlementMethod"

    data class PropertyAction(
        val propertyId: String,
        val propertyName: String,
        val settlementValue: Int,
        val destination: String,
        val soldToBank: Boolean,
    )

    data class Metadata(
        val settlementId: String,
        val debtorPlayerId: String,
        val creditorPlayerId: String,
        val originalAmountDue: Int,
        val cashAmountUsed: Int,
        val propertyValueUsed: Int,
        val remainingDue: Int,
        val reason: DebtReason,
        val propertyActions: List<PropertyAction>,
        val settlementMethod: DebtAssetSettlementMethod = DebtAssetSettlementMethod.TRANSFER_TO_CREDITOR,
    )

    fun stateAfter(metadata: Metadata): JsonObject = buildJsonObject {
        put(SETTLEMENT_ID, metadata.settlementId)
        put(DEBTOR_PLAYER_ID, metadata.debtorPlayerId)
        put(CREDITOR_PLAYER_ID, metadata.creditorPlayerId)
        put(ORIGINAL_AMOUNT_DUE, metadata.originalAmountDue)
        put(CASH_AMOUNT_USED, metadata.cashAmountUsed)
        put(PROPERTY_VALUE_USED, metadata.propertyValueUsed)
        put(REMAINING_DUE, metadata.remainingDue)
        put(REASON, metadata.reason.name)
            put(SETTLEMENT_METHOD, metadata.settlementMethod.name)
        put(PROPERTY_ACTIONS, propertyActionsJson(metadata.propertyActions))
    }

    fun fromTransaction(tx: Transaction): Metadata? {
        if (tx.transactionType != TransactionType.RENT_DEBT_SETTLED) return null
        val settlementId = tx.stateAfter[SETTLEMENT_ID]?.jsonPrimitive?.content ?: tx.transactionId
        val debtorPlayerId = tx.stateAfter[DEBTOR_PLAYER_ID]?.jsonPrimitive?.content ?: tx.playerId ?: return null
        val creditorPlayerId = tx.stateAfter[CREDITOR_PLAYER_ID]?.jsonPrimitive?.content ?: tx.toEntity ?: return null
        val originalAmountDue = tx.stateAfter[ORIGINAL_AMOUNT_DUE]?.jsonPrimitive?.intOrNull ?: return null
        val cashAmountUsed = tx.stateAfter[CASH_AMOUNT_USED]?.jsonPrimitive?.intOrNull ?: 0
        val propertyValueUsed = tx.stateAfter[PROPERTY_VALUE_USED]?.jsonPrimitive?.intOrNull ?: 0
        val remainingDue = tx.stateAfter[REMAINING_DUE]?.jsonPrimitive?.intOrNull ?: 0
        val reason = tx.stateAfter[REASON]?.jsonPrimitive?.content?.let {
            runCatching { DebtReason.valueOf(it) }.getOrNull()
        } ?: DebtReason.GENERIC
        val settlementMethod = tx.stateAfter[SETTLEMENT_METHOD]?.jsonPrimitive?.content?.let {
            runCatching { DebtAssetSettlementMethod.valueOf(it) }.getOrNull()
        } ?: if (propertyActionsFrom(tx).any { it.soldToBank }) {
            DebtAssetSettlementMethod.SELL_TO_BANK
        } else {
            DebtAssetSettlementMethod.TRANSFER_TO_CREDITOR
        }
        return Metadata(
            settlementId = settlementId,
            debtorPlayerId = debtorPlayerId,
            creditorPlayerId = creditorPlayerId,
            originalAmountDue = originalAmountDue,
            cashAmountUsed = cashAmountUsed,
            propertyValueUsed = propertyValueUsed,
            remainingDue = remainingDue,
            reason = reason,
            propertyActions = parsePropertyActions(tx.stateAfter[PROPERTY_ACTIONS]?.jsonArray),
            settlementMethod = settlementMethod,
        )
    }

    private fun propertyActionsFrom(tx: Transaction): List<PropertyAction> =
        parsePropertyActions(tx.stateAfter[PROPERTY_ACTIONS]?.jsonArray)

    private fun propertyActionsJson(actions: List<PropertyAction>): JsonArray = buildJsonArray {
        actions.forEach { action ->
            add(
                buildJsonObject {
                    put("propertyId", action.propertyId)
                    put("propertyName", action.propertyName)
                    put("settlementValue", action.settlementValue)
                    put("destination", action.destination)
                    put("soldToBank", action.soldToBank)
                },
            )
        }
    }

    private fun parsePropertyActions(array: JsonArray?): List<PropertyAction> =
        array?.mapNotNull { parsePropertyAction(it.jsonObject) } ?: emptyList()

    private fun parsePropertyAction(obj: JsonObject): PropertyAction? {
        val propertyId = obj["propertyId"]?.jsonPrimitive?.content ?: return null
        val propertyName = obj["propertyName"]?.jsonPrimitive?.content ?: return null
        val settlementValue = obj["settlementValue"]?.jsonPrimitive?.intOrNull ?: return null
        val destination = obj["destination"]?.jsonPrimitive?.content ?: return null
        val soldToBank = obj["soldToBank"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: (destination == EntityRef.BANK)
        return PropertyAction(
            propertyId = propertyId,
            propertyName = propertyName,
            settlementValue = settlementValue,
            destination = destination,
            soldToBank = soldToBank,
        )
    }
}
