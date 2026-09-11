package com.boardbanker.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on [TransactionType.EVENT_APPLIED] for direct-to-Jail events that end the turn. */
object DirectJailEventSnapshot {
    private const val AFFECTED_PLAYER_ID = "affectedPlayerId"
    private const val DESTINATION = "destination"
    private const val COLLECTED_GO = "collectedGo"
    private const val TURN_ENDED = "turnEnded"

    const val SUBTITLE = "Sent directly to Jail • Turn ended • No GO collected"

    data class Metadata(
        val affectedPlayerId: String,
        val destination: String,
        val collectedGo: Boolean,
        val turnEnded: Boolean,
    )

    fun stateAfter(
        affectedPlayerId: String,
        destination: String = EntityRef.JAIL,
        collectedGo: Boolean = false,
        turnEnded: Boolean = true,
    ): JsonObject = buildJsonObject {
        put(AFFECTED_PLAYER_ID, affectedPlayerId)
        put(DESTINATION, destination)
        put(COLLECTED_GO, collectedGo)
        put(TURN_ENDED, turnEnded)
    }

    fun fromEventApplied(tx: Transaction): Metadata? {
        if (tx.transactionType != TransactionType.EVENT_APPLIED) return null
        val affectedPlayerId = tx.stateAfter[AFFECTED_PLAYER_ID]?.jsonPrimitive?.content ?: return null
        val destination = tx.stateAfter[DESTINATION]?.jsonPrimitive?.content ?: EntityRef.JAIL
        val collectedGo = tx.stateAfter[COLLECTED_GO]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val turnEnded = tx.stateAfter[TURN_ENDED]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return Metadata(
            affectedPlayerId = affectedPlayerId,
            destination = destination,
            collectedGo = collectedGo,
            turnEnded = turnEnded,
        )
    }

    fun isDirectJailWithTurnEnd(tx: Transaction): Boolean {
        val metadata = fromEventApplied(tx) ?: return false
        return metadata.turnEnded && metadata.destination == EntityRef.JAIL
    }
}
