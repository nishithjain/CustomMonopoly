package com.boardbanker.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Metadata on ownership-change transactions created during debt settlement. */
object DebtPropertySettlementSnapshot {
    private const val PROPERTY_ID = "propertyId"
    private const val PROPERTY_NAME = "propertyName"
    private const val SETTLEMENT_VALUE = "settlementValue"
    private const val DESTINATION = "destination"
    private const val SOLD_TO_BANK = "soldToBank"
    private const val ASSET_KIND = "assetKind"

    enum class AssetKind { PROPERTY, ENERGY_GRID }

    data class Metadata(
        val propertyId: String,
        val propertyName: String,
        val settlementValue: Int,
        val destination: String,
        val soldToBank: Boolean,
        val assetKind: AssetKind,
    )

    fun stateAfter(
        propertyId: String,
        propertyName: String,
        settlementValue: Int,
        destination: String,
        soldToBank: Boolean,
        assetKind: AssetKind,
    ): JsonObject = buildJsonObject {
        put(PROPERTY_ID, propertyId)
        put(PROPERTY_NAME, propertyName)
        put(SETTLEMENT_VALUE, settlementValue)
        put(DESTINATION, destination)
        put(SOLD_TO_BANK, soldToBank)
        put(ASSET_KIND, assetKind.name)
    }

    fun fromTransaction(tx: Transaction): Metadata? {
        if (tx.transactionType != TransactionType.PROPERTY_OWNERSHIP_CHANGE &&
            tx.transactionType != TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE
        ) {
            return null
        }
        val propertyId = tx.stateAfter[PROPERTY_ID]?.jsonPrimitive?.content ?: tx.propertyId ?: return null
        val propertyName = tx.stateAfter[PROPERTY_NAME]?.jsonPrimitive?.content ?: return null
        val settlementValue = tx.stateAfter[SETTLEMENT_VALUE]?.jsonPrimitive?.intOrNull ?: tx.amount ?: return null
        val destination = tx.stateAfter[DESTINATION]?.jsonPrimitive?.content ?: tx.toEntity ?: return null
        val soldToBank = tx.stateAfter[SOLD_TO_BANK]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: (destination == EntityRef.BANK)
        val assetKind = tx.stateAfter[ASSET_KIND]?.jsonPrimitive?.content?.let {
            runCatching { AssetKind.valueOf(it) }.getOrNull()
        } ?: when (tx.transactionType) {
            TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE -> AssetKind.ENERGY_GRID
            else -> AssetKind.PROPERTY
        }
        return Metadata(
            propertyId = propertyId,
            propertyName = propertyName,
            settlementValue = settlementValue,
            destination = destination,
            soldToBank = soldToBank,
            assetKind = assetKind,
        )
    }

    fun isDebtSettlement(tx: Transaction): Boolean = fromTransaction(tx) != null
}
