package com.boardbanker.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object RentLevelChangeSnapshot {
    const val OLD_LEVEL_KEY = "oldRentLevel"
    const val NEW_LEVEL_KEY = "newRentLevel"

    fun stateBefore(oldLevel: Int): JsonObject = buildJsonObject {
        put(OLD_LEVEL_KEY, oldLevel)
    }

    fun stateAfter(newLevel: Int): JsonObject = buildJsonObject {
        put(NEW_LEVEL_KEY, newLevel)
    }

    fun oldLevel(transaction: Transaction): Int? =
        transaction.stateBefore[OLD_LEVEL_KEY]?.jsonPrimitive?.content?.toIntOrNull()

    fun newLevel(transaction: Transaction): Int? =
        transaction.stateAfter[NEW_LEVEL_KEY]?.jsonPrimitive?.content?.toIntOrNull()
            ?: transaction.amount

    fun levelChangeText(oldLevel: Int?, newLevel: Int): String =
        if (oldLevel != null) {
            "From L$oldLevel → L$newLevel"
        } else {
            "To L$newLevel"
        }
}
