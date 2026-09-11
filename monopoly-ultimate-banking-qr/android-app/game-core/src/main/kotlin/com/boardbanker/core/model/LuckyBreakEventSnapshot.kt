package com.boardbanker.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Structured metadata on Lucky Break bank transactions for Recent Banking. */
object LuckyBreakEventSnapshot {
    private const val PLAYER_ID = "playerId"
    private const val EVENT_ID = "eventId"
    private const val EVENT_NAME = "eventName"
    private const val MODE = "mode"
    private const val OUTCOME = "outcome"
    private const val APPLIED_AMOUNT = "appliedAmount"
    private const val ATTEMPT_NUMBER = "attemptNumber"
    private const val DIE_ONE = "dieOne"
    private const val DIE_TWO = "dieTwo"
    private const val RESOLUTION_ID = "resolutionId"

    data class Metadata(
        val playerId: String,
        val eventId: String,
        val eventName: String,
        val mode: DiceGambleMode,
        val outcome: LuckyBreakOutcome,
        val appliedAmount: Int,
        val attemptNumber: Int,
        val dieOne: Int?,
        val dieTwo: Int?,
        val resolutionId: String,
    )

    fun stateAfter(
        playerId: String,
        eventId: String,
        eventName: String,
        mode: DiceGambleMode,
        outcome: LuckyBreakOutcome,
        appliedAmount: Int,
        attemptNumber: Int,
        resolutionId: String,
        dieOne: Int? = null,
        dieTwo: Int? = null,
    ): JsonObject = buildJsonObject {
        put(PLAYER_ID, playerId)
        put(EVENT_ID, eventId)
        put(EVENT_NAME, eventName)
        put(MODE, mode.name)
        put(OUTCOME, outcome.name)
        put(APPLIED_AMOUNT, appliedAmount)
        put(ATTEMPT_NUMBER, attemptNumber)
        put(RESOLUTION_ID, resolutionId)
        dieOne?.let { put(DIE_ONE, it) }
        dieTwo?.let { put(DIE_TWO, it) }
    }

    fun fromTransaction(tx: Transaction): Metadata? {
        if (tx.transactionType != TransactionType.BANK_CREDIT &&
            tx.transactionType != TransactionType.BANK_DEBIT
        ) {
            return null
        }
        val playerId = tx.stateAfter[PLAYER_ID]?.jsonPrimitive?.content ?: return null
        val eventId = tx.stateAfter[EVENT_ID]?.jsonPrimitive?.content ?: tx.eventId ?: return null
        val eventName = tx.stateAfter[EVENT_NAME]?.jsonPrimitive?.content ?: return null
        val mode = tx.stateAfter[MODE]?.jsonPrimitive?.content?.let {
            runCatching { DiceGambleMode.valueOf(it) }.getOrNull()
        } ?: return null
        val outcome = tx.stateAfter[OUTCOME]?.jsonPrimitive?.content?.let {
            runCatching { LuckyBreakOutcome.valueOf(it) }.getOrNull()
        } ?: return null
        val appliedAmount = tx.stateAfter[APPLIED_AMOUNT]?.jsonPrimitive?.intOrNull ?: tx.amount ?: return null
        val attemptNumber = tx.stateAfter[ATTEMPT_NUMBER]?.jsonPrimitive?.intOrNull ?: return null
        val resolutionId = tx.stateAfter[RESOLUTION_ID]?.jsonPrimitive?.content ?: return null
        val dieOne = tx.stateAfter[DIE_ONE]?.jsonPrimitive?.intOrNull
        val dieTwo = tx.stateAfter[DIE_TWO]?.jsonPrimitive?.intOrNull
        return Metadata(
            playerId = playerId,
            eventId = eventId,
            eventName = eventName,
            mode = mode,
            outcome = outcome,
            appliedAmount = appliedAmount,
            attemptNumber = attemptNumber,
            dieOne = dieOne,
            dieTwo = dieTwo,
            resolutionId = resolutionId,
        )
    }

    fun isLuckyBreakResolution(tx: Transaction): Boolean = fromTransaction(tx) != null
}
