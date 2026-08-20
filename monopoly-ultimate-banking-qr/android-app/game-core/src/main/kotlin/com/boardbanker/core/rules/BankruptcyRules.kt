package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class BankruptcyRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val winnerCalculator: WinnerCalculator,
) {
    fun declareBankruptcy(
        session: GameSession,
        bankruptPlayerId: String,
        creditorId: String,
        amountOwed: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtRules.DebtResult {
        val player = session.players[bankruptPlayerId]
            ?: return DebtRules.DebtResult.failure("Unknown player")

        val updatedPlayer = player.copy(bankrupt = true, active = false)
        var updatedSession = session.copy(
            players = session.players + (bankruptPlayerId to updatedPlayer),
            status = GameStatus.FINISHED,
            debtResolution = null,
        )

        if (creditorId != EntityRef.BANK && creditorId != bankruptPlayerId) {
            val creditor = updatedSession.players[creditorId]!!
            val shortfall = amountOwed - player.balance -
                session.properties.values
                    .filter { it.ownerPlayerId == bankruptPlayerId }
                    .sumOf { definitions.properties[it.propertyId]!!.purchasePrice }
            if (shortfall > 0) {
                updatedSession = updatedSession.copy(
                    players = updatedSession.players + (
                        creditorId to creditor.copy(balance = creditor.balance + shortfall)
                    ),
                )
            }
        }

        val winnerId = winnerCalculator.determineWinner(updatedSession)
        updatedSession = updatedSession.copy(winnerPlayerId = winnerId)

        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANKRUPTCY,
            timestamp = timestamp,
            playerId = bankruptPlayerId,
            amount = amountOwed,
        )
        return DebtRules.DebtResult.success(sessionAfterTx, listOf(tx))
    }

    fun canCoverDebt(session: GameSession, playerId: String, amount: Int): Boolean {
        val cash = session.players[playerId]?.balance ?: 0
        val propertyValue = session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.properties[it.propertyId]!!.purchasePrice }
        return cash + propertyValue >= amount
    }
}
