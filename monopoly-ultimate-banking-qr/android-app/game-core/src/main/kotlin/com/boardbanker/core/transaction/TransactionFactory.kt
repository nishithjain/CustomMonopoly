package com.boardbanker.core.transaction

import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import kotlinx.serialization.json.JsonObject

class TransactionFactory {

    fun create(
        session: GameSession,
        type: TransactionType,
        timestamp: Long = System.currentTimeMillis(),
        fromEntity: String? = null,
        toEntity: String? = null,
        playerId: String? = null,
        propertyId: String? = null,
        eventId: String? = null,
        amount: Int? = null,
        stateBefore: JsonObject = JsonObject(emptyMap()),
        stateAfter: JsonObject = JsonObject(emptyMap()),
        reversible: Boolean = false,
    ): Pair<Transaction, GameSession> {
        val nextCounter = session.transactionCounter + 1
        val transaction = Transaction(
            transactionId = "${session.gameId}_TX_$nextCounter",
            gameId = session.gameId,
            timestamp = timestamp,
            transactionType = type,
            fromEntity = fromEntity,
            toEntity = toEntity,
            playerId = playerId,
            propertyId = propertyId,
            eventId = eventId,
            amount = amount,
            stateBefore = stateBefore,
            stateAfter = stateAfter,
            reversible = reversible,
        )
        return transaction to session.copy(
            transactionCounter = nextCounter,
            transactions = session.transactions + transaction,
        )
    }
}
