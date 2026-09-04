package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class EnergyGridRentRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val debtRules: DebtRules,
) {
    private val policies = definitions.policies

    fun processVisitorRent(
        session: GameSession,
        visitorId: String,
        energyGridId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): RentResult {
        val visitor = session.players[visitorId]
            ?: return RentResult.failure("Unknown visitor")
        val gridState = session.energyGrids[energyGridId]
            ?: return RentResult.failure("Unknown energy grid")
        val ownerId = gridState.ownerPlayerId
            ?: return RentResult.failure("Energy grid is unowned")
        val owner = session.players[ownerId]
            ?: return RentResult.failure("Unknown owner")

        JailGameplayGuard.boardActionBlockedMessage(definitions, session, visitorId)?.let {
            return RentResult.failure(it)
        }

        if (policies.rent.jailedOwnerCannotCollectRent() && owner.jailStatus) {
            return RentResult.success(session, emptyList(), 0)
        }

        val rentAmount = EnergyGridRentCalculator.rentForOwner(definitions, session, ownerId)

        if (rentAmount > 0 && visitor.pendingRentWaiver) {
            val updatedVisitor = visitor.copy(pendingRentWaiver = false)
            return RentResult.success(
                session.copy(
                    players = session.players + (visitorId to updatedVisitor),
                    undoSnapshot = session.snapshot(),
                ),
                emptyList(),
                0,
            )
        }

        if (visitor.balance < rentAmount) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = visitorId,
                creditorId = ownerId,
                amount = rentAmount,
                reason = com.boardbanker.core.model.DebtReason.RENT,
                propertyId = energyGridId,
                timestamp = timestamp,
            )
            if (!debtResult.isSuccess) {
                return RentResult.failure(debtResult.error ?: "Debt entry failed")
            }
            return RentResult.success(debtResult.session!!, debtResult.transactions, rentAmount)
        }

        val updatedVisitor = visitor.copy(balance = visitor.balance - rentAmount)
        val updatedOwner = owner.copy(balance = owner.balance + rentAmount)
        var updatedSession = session.copy(
            players = session.players + (visitorId to updatedVisitor) + (ownerId to updatedOwner),
        )

        val (rentTx, sessionAfterRent) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.RENT_PAYMENT,
            timestamp = timestamp,
            fromEntity = visitorId,
            toEntity = ownerId,
            playerId = visitorId,
            propertyId = energyGridId,
            amount = rentAmount,
            reversible = true,
        )
        updatedSession = sessionAfterRent.copy(undoSnapshot = session.snapshot())

        return RentResult.success(updatedSession, listOf(rentTx), rentAmount)
    }

    data class RentResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val rentAmount: Int,
        val error: String?,
    ) {
        companion object {
            fun success(session: GameSession, transactions: List<Transaction>, rentAmount: Int) =
                RentResult(session, transactions, rentAmount, null)

            fun failure(message: String) = RentResult(null, emptyList(), 0, message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
