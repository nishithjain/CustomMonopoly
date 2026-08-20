package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class GoRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
  private val goSalary = definitions.rulesConfig.goSalary

    fun payGoSalary(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): GoResult {
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
