package com.boardbanker.core.rules

import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.TemporaryEffect
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class RentRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val debtRules: DebtRules,
) {
    private val rules = definitions.rules
    private val policies = definitions.policies

    fun processVisitorRent(
        session: GameSession,
        visitorId: String,
        propertyId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): RentResult {
        val visitor = session.players[visitorId]
            ?: return RentResult.failure("Unknown visitor")
        val propertyState = session.properties[propertyId]
            ?: return RentResult.failure("Unknown property")
        val ownerId = propertyState.ownerPlayerId
            ?: return RentResult.failure("Property is unowned")
        val owner = session.players[ownerId]
            ?: return RentResult.failure("Unknown owner")
        val propertyDef = definitions.properties[propertyId]!!

        if (policies.rent.jailedOwnerCannotCollectRent() && owner.jailStatus) {
            return RentResult.success(session, emptyList())
        }

        val chargeLevelOverride = RentLevelOperations.effectiveChargeLevel(
            propertyState,
            session.temporaryEffects,
        )
        val rentAmount = RentLevelOperations.rentAmount(
            propertyDef,
            propertyState,
            chargeLevelOverride,
        )

        if (visitor.balance < rentAmount) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = visitorId,
                creditorId = ownerId,
                amount = rentAmount,
                reason = com.boardbanker.core.model.DebtReason.RENT,
                propertyId = propertyId,
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

        val transactions = mutableListOf<Transaction>()
        val (rentTx, sessionAfterRent) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.RENT_PAYMENT,
            timestamp = timestamp,
            fromEntity = visitorId,
            toEntity = ownerId,
            playerId = visitorId,
            propertyId = propertyId,
            amount = rentAmount,
            reversible = true,
        )
        transactions += rentTx
        updatedSession = sessionAfterRent

        val oldLevel = propertyState.currentRentLevel
        val newLevel = RentLevelOperations.increaseLevel(
            oldLevel,
            1,
            rules.maximumRentLevel,
        )
        val updatedProperty = propertyState.copy(currentRentLevel = newLevel)
        updatedSession = updatedSession.copy(
            properties = updatedSession.properties + (propertyId to updatedProperty),
        )
        val (levelTx, sessionAfterLevel) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            timestamp = timestamp,
            playerId = ownerId,
            propertyId = propertyId,
            amount = newLevel,
            stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
            stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
        )
        transactions += levelTx
        updatedSession = sessionAfterLevel

        if (chargeLevelOverride != null) {
            val effectIndex = updatedSession.temporaryEffects.indexOfFirst {
                it.effectType == "FORCE_LEVEL_1_RENT" && it.active && it.remainingUses > 0
            }
            if (effectIndex >= 0) {
                val effect = updatedSession.temporaryEffects[effectIndex]
                val newUses = effect.remainingUses - 1
                val updatedEffect = effect.copy(
                    remainingUses = newUses,
                    active = newUses > 0,
                )
                val newEffects = updatedSession.temporaryEffects.toMutableList()
                newEffects[effectIndex] = updatedEffect
                updatedSession = updatedSession.copy(temporaryEffects = newEffects)
                val (consumeTx, sessionAfterConsume) = transactionFactory.create(
                    session = updatedSession,
                    type = TransactionType.TEMPORARY_EFFECT_CONSUMED,
                    timestamp = timestamp,
                    eventId = effect.createdByEventId,
                    amount = newUses,
                )
                transactions += consumeTx
                updatedSession = sessionAfterConsume
            }
        }

        return RentResult.success(
            updatedSession.copy(undoSnapshot = session.snapshot()),
            transactions,
            rentAmount,
        )
    }

    data class RentResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val rentAmount: Int? = null,
        val error: String?,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<Transaction>,
                rentAmount: Int? = null,
            ) = RentResult(session, transactions, rentAmount, null)

            fun failure(message: String) = RentResult(null, emptyList(), null, message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
