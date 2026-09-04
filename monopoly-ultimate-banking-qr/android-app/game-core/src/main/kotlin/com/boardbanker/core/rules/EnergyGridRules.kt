package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class EnergyGridRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
    fun purchaseEnergyGrid(
        session: GameSession,
        buyerId: String,
        energyGridId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): EnergyGridPurchaseResult {
        val gridDef = definitions.energyGrids[energyGridId]
            ?: return EnergyGridPurchaseResult.failure("Unknown energy grid $energyGridId")
        val buyer = session.players[buyerId]
            ?: return EnergyGridPurchaseResult.failure("Unknown player $buyerId")
        val gridState = session.energyGrids[energyGridId]
            ?: return EnergyGridPurchaseResult.failure("Energy grid state missing $energyGridId")

        if (session.status != com.boardbanker.core.model.GameStatus.ACTIVE) {
            return EnergyGridPurchaseResult.failure("Game is not active")
        }
        if (gridState.ownerPlayerId != null) {
            return EnergyGridPurchaseResult.failure("Energy grid already owned")
        }
        if (!buyer.active || buyer.bankrupt) {
            return EnergyGridPurchaseResult.failure("Buyer is not active")
        }
        JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, buyerId)?.let {
            return EnergyGridPurchaseResult.failure(it)
        }
        session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }?.let { activePlayerId ->
            if (activePlayerId != buyerId) {
                val name = definitions.players[buyerId]?.displayName ?: buyerId
                return EnergyGridPurchaseResult.failure("It is not $name's turn.")
            }
        }
        if (buyer.balance < gridDef.purchasePrice) {
            return EnergyGridPurchaseResult.failure("Insufficient funds")
        }

        val price = gridDef.purchasePrice
        val updatedBuyer = buyer.copy(balance = buyer.balance - price)
        val updatedGrid = gridState.copy(ownerPlayerId = buyerId)
        var updatedSession = session.copy(
            players = session.players + (buyerId to updatedBuyer),
            energyGrids = session.energyGrids + (energyGridId to updatedGrid),
        )

        val (purchaseTx, sessionAfterPurchase) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.ENERGY_GRID_PURCHASE,
            timestamp = timestamp,
            fromEntity = buyerId,
            toEntity = EntityRef.BANK,
            playerId = buyerId,
            propertyId = energyGridId,
            amount = price,
            reversible = true,
        )
        updatedSession = sessionAfterPurchase

        return EnergyGridPurchaseResult.success(
            session = updatedSession.copy(undoSnapshot = session.snapshot()),
            transactions = listOf(purchaseTx),
        )
    }

    fun ownerLandsOnOwnGrid(
        session: GameSession,
        playerId: String,
        energyGridId: String,
    ): LandingResult = LandingResult.success(session, emptyList())

    data class EnergyGridPurchaseResult(
        val session: GameSession?,
        val transactions: List<com.boardbanker.core.model.Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<com.boardbanker.core.model.Transaction>,
            ) = EnergyGridPurchaseResult(session, transactions, null)

            fun failure(message: String) = EnergyGridPurchaseResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }

    data class LandingResult(
        val session: GameSession?,
        val transactions: List<com.boardbanker.core.model.Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<com.boardbanker.core.model.Transaction>,
            ) = LandingResult(session, transactions, null)

            fun failure(message: String) = LandingResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
