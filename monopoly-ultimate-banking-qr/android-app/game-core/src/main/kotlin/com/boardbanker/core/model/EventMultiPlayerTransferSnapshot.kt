package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [TransactionType.EVENT_MULTI_PLAYER_TRANSFER] for Recent Banking. */
object EventMultiPlayerTransferSnapshot {
    private const val TRANSFER_ID = "transferId"
    private const val EVENT_ID = "eventId"
    private const val EVENT_NAME = "eventName"
    private const val PAYER_PLAYER_ID = "payerPlayerId"
    private const val DIRECTION = "direction"
    private const val TRANSFERS = "transfers"
    private const val TOTAL_AMOUNT = "totalAmount"

    enum class Direction {
        PAY_EACH_PLAYER,
        COLLECT_FROM_EACH_PLAYER,
    }

    @Serializable
    data class Transfer(
        val fromPlayerId: String,
        val toPlayerId: String,
        val amount: Int,
    )

    data class Metadata(
        val transferId: String,
        val eventId: String,
        val eventName: String,
        val payerPlayerId: String,
        val direction: Direction,
        val transfers: List<Transfer>,
        val totalAmount: Int,
    )

    fun stateAfter(metadata: Metadata): JsonObject = buildJsonObject {
        put(TRANSFER_ID, metadata.transferId)
        put(EVENT_ID, metadata.eventId)
        put(EVENT_NAME, metadata.eventName)
        put(PAYER_PLAYER_ID, metadata.payerPlayerId)
        put(DIRECTION, metadata.direction.name)
        put(TOTAL_AMOUNT, metadata.totalAmount)
        put(TRANSFERS, transfersJson(metadata.transfers))
    }

    fun fromTransaction(tx: Transaction): Metadata? {
        if (tx.transactionType != TransactionType.EVENT_MULTI_PLAYER_TRANSFER) return null
        val transferId = tx.stateAfter[TRANSFER_ID]?.jsonPrimitive?.content ?: tx.transactionId
        val eventId = tx.stateAfter[EVENT_ID]?.jsonPrimitive?.content ?: tx.eventId ?: return null
        val eventName = tx.stateAfter[EVENT_NAME]?.jsonPrimitive?.content ?: return null
        val payerPlayerId = tx.stateAfter[PAYER_PLAYER_ID]?.jsonPrimitive?.content ?: tx.playerId ?: return null
        val direction = tx.stateAfter[DIRECTION]?.jsonPrimitive?.content?.let {
            runCatching { Direction.valueOf(it) }.getOrNull()
        } ?: return null
        val totalAmount = tx.stateAfter[TOTAL_AMOUNT]?.jsonPrimitive?.intOrNull ?: return null
        return Metadata(
            transferId = transferId,
            eventId = eventId,
            eventName = eventName,
            payerPlayerId = payerPlayerId,
            direction = direction,
            transfers = parseTransfers(tx.stateAfter[TRANSFERS]?.jsonArray),
            totalAmount = totalAmount,
        )
    }

    fun summaryText(metadata: Metadata, payerName: String): String = when (metadata.direction) {
        Direction.PAY_EACH_PLAYER -> "$payerName contributed to all other players"
        Direction.COLLECT_FROM_EACH_PLAYER -> "$payerName received birthday contributions"
    }

    fun totalLabel(metadata: Metadata, payerName: String, formattedTotal: String): String =
        when (metadata.direction) {
            Direction.PAY_EACH_PLAYER -> "Total paid: $formattedTotal"
            Direction.COLLECT_FROM_EACH_PLAYER -> "Total received by $payerName: $formattedTotal"
        }

    private fun transfersJson(transfers: List<Transfer>): JsonArray = buildJsonArray {
        transfers.forEach { transfer ->
            add(
                buildJsonObject {
                    put("fromPlayerId", transfer.fromPlayerId)
                    put("toPlayerId", transfer.toPlayerId)
                    put("amount", transfer.amount)
                },
            )
        }
    }

    private fun parseTransfers(array: JsonArray?): List<Transfer> =
        array?.mapNotNull { element ->
            val obj = element.jsonObject
            val fromPlayerId = obj["fromPlayerId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val toPlayerId = obj["toPlayerId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val amount = obj["amount"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            Transfer(fromPlayerId = fromPlayerId, toPlayerId = toPlayerId, amount = amount)
        } ?: emptyList()
}
