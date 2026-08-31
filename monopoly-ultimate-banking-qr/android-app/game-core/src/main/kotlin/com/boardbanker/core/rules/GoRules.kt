package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GoCollectionReason
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class GoRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
    private val goSalary = definitions.bankingValues.goSalary
    private val goPolicy = definitions.policies.go

    fun payGoSalary(
        session: GameSession,
        playerId: String,
        reason: GoCollectionReason = GoCollectionReason.PASS,
        timestamp: Long = System.currentTimeMillis(),
    ): GoResult {
        if (!isCollectionAllowed(reason)) {
            return GoResult.failure("GO collection is not allowed for ${reason.name.lowercase()} movement in this edition")
        }
        val player = session.players[playerId]
            ?: return GoResult.failure("Unknown player $playerId")
        if (!player.active || player.bankrupt) {
            return GoResult.failure("Player is not active")
        }

        val updatedPlayer = player.copy(balance = player.balance + goSalary)
        var updatedSession = session.copy(
            players = session.players + (playerId to updatedPlayer),
        )
        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_CREDIT,
            timestamp = timestamp,
            fromEntity = EntityRef.BANK,
            toEntity = playerId,
            playerId = playerId,
            amount = goSalary,
            reversible = true,
        )
        updatedSession = sessionAfterTx.copy(undoSnapshot = session.snapshot())

        return GoResult.success(updatedSession, listOf(tx))
    }

    fun isCollectionAllowed(reason: GoCollectionReason): Boolean = when (reason) {
        GoCollectionReason.PASS,
        GoCollectionReason.LAND,
        GoCollectionReason.MANUAL_BANK_ACTION,
        -> goPolicy.collectsGoForNormalDice()
        GoCollectionReason.EVENT_MOVE -> goPolicy.collectsGoForEventMovement()
        GoCollectionReason.LOCATION_MOVE -> goPolicy.collectsGoForLocationMovement()
    }

    data class GoResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(session: GameSession, transactions: List<Transaction>) =
                GoResult(session, transactions, null)

            fun failure(message: String) = GoResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
