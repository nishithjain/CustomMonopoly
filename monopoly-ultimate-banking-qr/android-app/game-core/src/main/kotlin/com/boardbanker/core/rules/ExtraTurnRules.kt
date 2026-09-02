package com.boardbanker.core.rules

import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.transaction.TransactionFactory

class ExtraTurnRules(
    private val transactionFactory: TransactionFactory,
) {
    fun cancelPendingExtraTurnOnJail(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): JailCancellationResult {
        val player = session.players[playerId] ?: return JailCancellationResult(session, emptyList())
        var updatedSession = session
        val transactions = mutableListOf<Transaction>()

        if (player.pendingExtraTurn) {
            updatedSession = updatedSession.copy(
                players = updatedSession.players + (playerId to player.copy(pendingExtraTurn = false)),
            )
            val (cancelTx, sessionAfterCancel) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL,
                timestamp = timestamp,
                playerId = playerId,
                reversible = true,
            )
            updatedSession = sessionAfterCancel
            transactions += cancelTx
        }

        val turnState = updatedSession.turnState
        if (turnState?.activePlayerId == playerId && turnState.turnKind == TurnKind.EXTRA) {
            updatedSession = updatedSession.copy(
                turnState = turnState.copy(turnKind = TurnKind.NORMAL),
            )
        }

        return JailCancellationResult(updatedSession, transactions)
    }

    fun beginTurn(session: GameSession): GameSession = session.copy(
        pendingEventChoice = null,
        pendingEventExecution = null,
        pendingEventDraw = null,
        pendingDiceGamble = null,
        eventChainDepth = 0,
    )

    data class JailCancellationResult(
        val session: GameSession,
        val transactions: List<Transaction>,
    )
}
