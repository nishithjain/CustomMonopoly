package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class JailRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val extraTurnRules: ExtraTurnRules = ExtraTurnRules(transactionFactory),
) {
    private val jailFee = definitions.bankingValues.jailReleaseFee

    fun sendToJail(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): JailResult {
        val player = session.players[playerId]
            ?: return JailResult.failure("Unknown player")
        if (player.jailStatus) {
            return JailResult.success(session, emptyList())
        }
        val updatedPlayer = player.copy(jailStatus = true)
        var updatedSession = session.copy(
            players = session.players + (playerId to updatedPlayer),
        )
        val extraTurnCancellation = extraTurnRules.cancelPendingExtraTurnOnJail(
            session = updatedSession,
            playerId = playerId,
            timestamp = timestamp,
        )
        updatedSession = extraTurnCancellation.session
        val extraTurnTransactions = extraTurnCancellation.transactions
        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.JAIL_STATUS_CHANGE,
            timestamp = timestamp,
            playerId = playerId,
        )
        return JailResult.success(sessionAfterTx, extraTurnTransactions + tx)
    }

    fun payJailFee(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): JailResult {
        val player = session.players[playerId]
            ?: return JailResult.failure("Unknown player")
        if (!player.jailStatus) {
            return JailResult.failure("Player is not in jail")
        }
        if (player.balance < jailFee) {
            return JailResult.insufficientFunds(playerId, jailFee, player.balance)
        }

        val updatedPlayer = player.copy(
            balance = player.balance - jailFee,
            jailStatus = false,
        )
        var updatedSession = session.copy(
            players = session.players + (playerId to updatedPlayer),
        )
        val (debitTx, sessionAfterDebit) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_DEBIT,
            timestamp = timestamp,
            fromEntity = playerId,
            toEntity = EntityRef.BANK,
            playerId = playerId,
            amount = jailFee,
            reversible = true,
        )
        updatedSession = sessionAfterDebit
        val (jailTx, sessionAfterJail) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.JAIL_STATUS_CHANGE,
            timestamp = timestamp,
            playerId = playerId,
        )
        return JailResult.success(
            sessionAfterJail.copy(undoSnapshot = session.snapshot()),
            listOf(debitTx, jailTx),
        )
    }

    fun useGetOutOfJailPass(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): JailResult {
        val player = session.players[playerId]
            ?: return JailResult.failure("Unknown player")
        if (!player.jailStatus) {
            return JailResult.failure("Player is not in jail")
        }
        if (player.jailPassCount <= 0) {
            return JailResult.failure("Player has no Get Out of Jail pass")
        }

        val updatedPlayer = player.copy(
            jailPassCount = player.jailPassCount - 1,
            jailStatus = false,
        )
        var updatedSession = session.copy(
            players = session.players + (playerId to updatedPlayer),
        )
        val (passTx, sessionAfterPass) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.JAIL_PASS_USED,
            timestamp = timestamp,
            playerId = playerId,
            amount = 1,
            reversible = true,
        )
        updatedSession = sessionAfterPass
        val (jailTx, sessionAfterJail) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.JAIL_STATUS_CHANGE,
            timestamp = timestamp,
            playerId = playerId,
            reversible = true,
        )
        return JailResult.success(
            sessionAfterJail.copy(undoSnapshot = session.snapshot()),
            listOf(passTx, jailTx),
        )
    }

    fun releaseByDoubles(
        session: GameSession,
        playerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): JailResult {
        val player = session.players[playerId]
            ?: return JailResult.failure("Unknown player")
        if (!player.jailStatus) {
            return JailResult.failure("Player is not in jail")
        }
        val updatedPlayer = player.copy(jailStatus = false)
        var updatedSession = session.copy(
            players = session.players + (playerId to updatedPlayer),
        )
        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.JAIL_STATUS_CHANGE,
            timestamp = timestamp,
            playerId = playerId,
        )
        return JailResult.success(sessionAfterTx, listOf(tx))
    }

    data class JailResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val error: String?,
        val needsDebtResolution: Boolean = false,
        val debtAmount: Int = 0,
    ) {
        companion object {
            fun success(session: GameSession, transactions: List<Transaction>) =
                JailResult(session, transactions, null)

            fun failure(message: String) = JailResult(null, emptyList(), message)

            fun insufficientFunds(playerId: String, required: Int, available: Int) =
                JailResult(
                    session = null,
                    transactions = emptyList(),
                    error = "Insufficient funds for jail fee",
                    needsDebtResolution = true,
                    debtAmount = required,
                )
        }

        val isSuccess: Boolean get() = session != null
    }
}
