package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class UndoSupport(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
    private val undoPolicy = definitions.policies.undo

    fun canUndo(session: GameSession): Boolean {
        if (undoPolicy.blockedDuringDebtResolution() && session.debtResolution != null) return false
        val lastTx = session.transactions.lastOrNull() ?: return false
        if (session.undoSnapshot == null) return false
        if (lastTx.transactionType == TransactionType.RENT_DEBT_SETTLED ||
            lastTx.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        ) {
            return true
        }
        if (undoPolicy.isIneligible(lastTx.transactionType)) {
            return trailingEventAppliedWithUndoableAction(session.transactions)
        }
        return undoPolicy.isEligible(lastTx.transactionType) ||
            session.transactions.takeLast(2).any { undoPolicy.isEligible(it.transactionType) }
    }

    fun undo(session: GameSession, timestamp: Long = System.currentTimeMillis()): UndoResult {
        if (undoPolicy.blockedDuringDebtResolution() && session.debtResolution != null) {
            return UndoResult.failure("Undo blocked during debt resolution")
        }
        val snapshot = session.undoSnapshot
            ?: return UndoResult.failure("No undo snapshot available")
        val lastTx = session.transactions.lastOrNull()
        if (lastTx != null && undoPolicy.isIneligible(lastTx.transactionType) &&
            !trailingEventAppliedWithUndoableAction(session.transactions)
        ) {
            return UndoResult.failure("Event transactions are not undoable")
        }

        val restored = session.restoreFrom(snapshot).copy(
            transactions = session.transactions,
            transactionCounter = session.transactionCounter,
        )
        val (tx, sessionAfterTx) = transactionFactory.create(
            session = restored,
            type = TransactionType.UNDO,
            timestamp = timestamp,
            reversible = false,
        )
        return UndoResult.success(sessionAfterTx.copy(undoSnapshot = null), listOf(tx))
    }

    private fun trailingEventAppliedWithUndoableAction(transactions: List<Transaction>): Boolean {
        val lastTx = transactions.lastOrNull() ?: return false
        if (lastTx.transactionType != TransactionType.EVENT_APPLIED) return false
        return transactions.dropLast(1).any { tx ->
            tx.transactionType == TransactionType.RENT_DEBT_SETTLED ||
                tx.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER ||
                undoPolicy.isEligible(tx.transactionType)
        }
    }

    data class UndoResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(session: GameSession, transactions: List<Transaction>) =
                UndoResult(session, transactions, null)

            fun failure(message: String) = UndoResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
