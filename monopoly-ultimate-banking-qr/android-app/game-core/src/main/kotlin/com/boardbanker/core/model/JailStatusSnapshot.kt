package com.boardbanker.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured jail-state metadata on [TransactionType.JAIL_STATUS_CHANGE] rows. */
object JailStatusSnapshot {
    private const val IN_JAIL_KEY = "inJail"

    fun stateBefore(inJail: Boolean): JsonObject = buildJsonObject { put(IN_JAIL_KEY, inJail) }

    fun stateAfter(inJail: Boolean): JsonObject = buildJsonObject { put(IN_JAIL_KEY, inJail) }

    fun wasInJailBefore(tx: Transaction): Boolean? =
        tx.stateBefore[IN_JAIL_KEY]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

    fun isInJailAfter(tx: Transaction): Boolean? =
        tx.stateAfter[IN_JAIL_KEY]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

    fun enteredJail(tx: Transaction): Boolean {
        val before = wasInJailBefore(tx)
        val after = isInJailAfter(tx)
        return before == false && after == true
    }

    fun releasedFromJail(tx: Transaction): Boolean {
        val before = wasInJailBefore(tx)
        val after = isInJailAfter(tx)
        return before == true && after == false
    }
}
