package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class PropertyRules(
    private val definitions: GameDefinitions,
    private val colorSetRules: ColorSetRules,
    private val transactionFactory: TransactionFactory,
) {
    private val rules = definitions.rulesConfig

    fun purchaseProperty(
        session: GameSession,
        buyerId: String,
        propertyId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): PropertyPurchaseResult {
        val propertyDef = definitions.properties[propertyId]
            ?: return PropertyPurchaseResult.failure("Unknown property $propertyId")
        val buyer = session.players[buyerId]
            ?: return PropertyPurchaseResult.failure("Unknown player $buyerId")
        val propertyState = session.properties[propertyId]
            ?: return PropertyPurchaseResult.failure("Property state missing $propertyId")

        if (session.status != com.boardbanker.core.model.GameStatus.ACTIVE) {
            return PropertyPurchaseResult.failure("Game is not active")
        }
        if (propertyState.ownerPlayerId != null) {
            return PropertyPurchaseResult.failure("Property already owned")
        }
        if (!buyer.active || buyer.bankrupt) {
            return PropertyPurchaseResult.failure("Buyer is not active")
        }
        if (buyer.balance < propertyDef.purchasePrice) {
            return PropertyPurchaseResult.failure("Insufficient funds")
        }

        val price = propertyDef.purchasePrice
        val updatedBuyer = buyer.copy(balance = buyer.balance - price)
        val updatedProperty = propertyState.copy(
            ownerPlayerId = buyerId,
            currentRentLevel = 1,
        )
        var updatedSession = session.copy(
            players = session.players + (buyerId to updatedBuyer),
            properties = session.properties + (propertyId to updatedProperty),
        )

        val (purchaseTx, sessionAfterPurchase) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.PROPERTY_PURCHASE,
            timestamp = timestamp,
            fromEntity = buyerId,
            toEntity = com.boardbanker.core.model.EntityRef.BANK,
            playerId = buyerId,
            propertyId = propertyId,
            amount = price,
            reversible = true,
        )
        updatedSession = sessionAfterPurchase

        val bonusResult = colorSetRules.applyCompletionBonusIfNeeded(
            session = updatedSession,
            purchasedPropertyId = propertyId,
            buyerId = buyerId,
            timestamp = timestamp,
        )
        updatedSession = bonusResult.session

        return PropertyPurchaseResult.success(
            session = updatedSession.copy(
                undoSnapshot = session.snapshot(),
            ),
            transactions = listOf(purchaseTx) + bonusResult.transactions,
        )
    }

    fun ownerLandsOnOwnProperty(
        session: GameSession,
        playerId: String,
        propertyId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): LandingResult {
        val player = session.players[playerId]
            ?: return LandingResult.failure("Unknown player")
        val propertyState = session.properties[propertyId]
            ?: return LandingResult.failure("Unknown property")

        if (propertyState.ownerPlayerId != playerId) {
            return LandingResult.failure("Player does not own property")
        }
        if (player.jailStatus) {
            return LandingResult.success(session, emptyList())
        }

        val oldLevel = propertyState.currentRentLevel
        val newLevel = RentLevelOperations.increaseLevel(
            oldLevel,
            1,
            rules.maximumRentLevel,
        )
        if (newLevel == oldLevel) {
            return LandingResult.success(session, emptyList())
        }

        val updatedProperty = propertyState.copy(currentRentLevel = newLevel)
        var updatedSession = session.copy(
            properties = session.properties + (propertyId to updatedProperty),
        )
        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            timestamp = timestamp,
            playerId = playerId,
            propertyId = propertyId,
            amount = newLevel,
            stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
            stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
        )
        updatedSession = sessionAfterTx

        return LandingResult.success(updatedSession, listOf(tx))
    }

    data class PropertyPurchaseResult(
        val session: GameSession?,
        val transactions: List<com.boardbanker.core.model.Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<com.boardbanker.core.model.Transaction>,
            ) = PropertyPurchaseResult(session, transactions, null)

            fun failure(message: String) = PropertyPurchaseResult(null, emptyList(), message)
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
