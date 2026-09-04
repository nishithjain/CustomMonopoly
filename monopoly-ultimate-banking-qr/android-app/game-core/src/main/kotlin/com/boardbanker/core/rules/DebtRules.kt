package com.boardbanker.core.rules

import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.EnergyGridState
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class DebtRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val bankruptcyRules: BankruptcyRules,
) {
    fun enterDebtResolution(
        session: GameSession,
        debtorId: String,
        creditorId: String,
        amount: Int,
        reason: DebtReason = DebtReason.GENERIC,
        propertyId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        val debtor = session.players[debtorId]
            ?: return DebtResult.failure("Unknown debtor")
        if (debtor.balance >= amount) {
            return DebtResult.failure("Debtor has sufficient funds")
        }

        val cashUsed = debtor.balance
        val remaining = amount - cashUsed
        val updatedDebtor = debtor.copy(balance = 0)
        var updatedSession = session.copy(
            players = session.players + (debtorId to updatedDebtor),
            debtResolution = DebtResolutionState(
                debtorPlayerId = debtorId,
                creditorPlayerId = creditorId,
                amountRemaining = remaining,
                reason = reason,
                propertyId = propertyId,
            ),
            undoSnapshot = null,
        )

        val transactions = mutableListOf<Transaction>()
        if (cashUsed > 0) {
            val (tx, sessionAfter) = transactionFactory.create(
                session = updatedSession,
                type = if (creditorId == EntityRef.BANK) TransactionType.BANK_DEBIT else TransactionType.RENT_PAYMENT,
                timestamp = timestamp,
                fromEntity = debtorId,
                toEntity = creditorId,
                playerId = debtorId,
                amount = cashUsed,
            )
            transactions += tx
            updatedSession = sessionAfter
        }

        return DebtResult.success(updatedSession, transactions)
    }

    fun resolveWithProperty(
        session: GameSession,
        propertyId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult = resolveWithProperties(session, propertyIds = listOf(propertyId), timestamp = timestamp)

    fun resolveWithProperties(
        session: GameSession,
        propertyIds: List<String> = emptyList(),
        energyGridIds: List<String> = emptyList(),
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        if (propertyIds.isEmpty() && energyGridIds.isEmpty()) {
            return DebtResult.failure("No assets selected")
        }
        if (propertyIds.size != propertyIds.toSet().size) {
            return DebtResult.failure("Duplicate property selected")
        }
        if (energyGridIds.size != energyGridIds.toSet().size) {
            return DebtResult.failure("Duplicate energy grid selected")
        }

        val debt = session.debtResolution
            ?: return DebtResult.failure("No debt resolution in progress")
        val debtorId = debt.debtorPlayerId
        val creditorId = debt.creditorPlayerId
        val undoSnapshotBeforeSettlement = session.snapshot()

        val selectedProperties = mutableListOf<Pair<String, PropertyState>>()
        val selectedEnergyGrids = mutableListOf<Pair<String, EnergyGridState>>()
        val selectedValues = mutableListOf<Int>()
        for (propertyId in propertyIds) {
            val propertyDef = definitions.properties[propertyId]
                ?: return DebtResult.failure("Unknown property")
            val propertyState = session.properties[propertyId]
                ?: return DebtResult.failure("Property state missing")
            if (propertyState.ownerPlayerId != debtorId) {
                return DebtResult.failure("Property not owned by debtor")
            }
            selectedProperties += propertyId to propertyState
            selectedValues += propertyDef.purchasePrice
        }
        for (energyGridId in energyGridIds) {
            val gridDef = definitions.energyGrids[energyGridId]
                ?: return DebtResult.failure("Unknown energy grid")
            val gridState = session.energyGrids[energyGridId]
                ?: return DebtResult.failure("Energy grid state missing")
            if (gridState.ownerPlayerId != debtorId) {
                return DebtResult.failure("Energy grid not owned by debtor")
            }
            selectedEnergyGrids += energyGridId to gridState
            selectedValues += gridDef.purchasePrice
        }

        val settlement = DebtSettlementCalculator.calculate(debt.amountRemaining, selectedValues)
        val transactions = mutableListOf<Transaction>()
        var updatedSession = session

        for ((energyGridId, gridState) in selectedEnergyGrids) {
            val valuation = definitions.energyGrids[energyGridId]!!.purchasePrice
            val updatedGrid = if (creditorId != EntityRef.BANK) {
                gridState.copy(ownerPlayerId = creditorId)
            } else {
                gridState.copy(ownerPlayerId = null)
            }
            updatedSession = updatedSession.copy(
                energyGrids = updatedSession.energyGrids + (energyGridId to updatedGrid),
            )
            val (ownershipTx, sessionAfterOwnership) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE,
                timestamp = timestamp,
                fromEntity = debtorId,
                toEntity = if (creditorId != EntityRef.BANK) creditorId else EntityRef.BANK,
                playerId = debtorId,
                propertyId = energyGridId,
                amount = valuation,
            )
            transactions += ownershipTx
            updatedSession = sessionAfterOwnership
        }

        for ((propertyId, propertyState) in selectedProperties) {
            val valuation = definitions.properties[propertyId]!!.purchasePrice
            if (creditorId != EntityRef.BANK) {
                val updatedProperty = propertyState.copy(ownerPlayerId = creditorId)
                updatedSession = updatedSession.copy(
                    properties = updatedSession.properties + (propertyId to updatedProperty),
                )
                val (ownershipTx, sessionAfterOwnership) = transactionFactory.create(
                    session = updatedSession,
                    type = TransactionType.PROPERTY_OWNERSHIP_CHANGE,
                    timestamp = timestamp,
                    fromEntity = debtorId,
                    toEntity = creditorId,
                    playerId = debtorId,
                    propertyId = propertyId,
                    amount = valuation,
                )
                transactions += ownershipTx
                updatedSession = sessionAfterOwnership
            } else {
                val updatedProperty = propertyState.copy(
                    ownerPlayerId = null,
                    currentRentLevel = 1,
                )
                updatedSession = updatedSession.copy(
                    properties = updatedSession.properties + (propertyId to updatedProperty),
                )
                val (ownershipTx, sessionAfterOwnership) = transactionFactory.create(
                    session = updatedSession,
                    type = TransactionType.PROPERTY_OWNERSHIP_CHANGE,
                    timestamp = timestamp,
                    fromEntity = debtorId,
                    toEntity = EntityRef.BANK,
                    playerId = debtorId,
                    propertyId = propertyId,
                    amount = valuation,
                )
                transactions += ownershipTx
                updatedSession = sessionAfterOwnership
            }
        }

        if (settlement.remainingDebt > 0) {
            updatedSession = updatedSession.copy(
                debtResolution = debt.copy(amountRemaining = settlement.remainingDebt),
            )
            return DebtResult.success(updatedSession, transactions)
        }

        if (settlement.changeAmount > 0) {
            val changeResult = applyDebtSettlementChange(
                session = updatedSession,
                debtorId = debtorId,
                creditorId = creditorId,
                changeAmount = settlement.changeAmount,
                timestamp = timestamp,
            )
            updatedSession = changeResult.session
            transactions += changeResult.transactions
        }

        val debtBeforeClear = updatedSession.debtResolution
        updatedSession = clearDebtAndMaybeReleaseJail(updatedSession, debtorId)
            .copy(undoSnapshot = undoSnapshotBeforeSettlement)
        if (debtBeforeClear != null && updatedSession.debtResolution == null) {
            val followUp = completeDebtReason(
                session = updatedSession,
                debt = debtBeforeClear,
                debtorId = debtorId,
                timestamp = timestamp,
            )
            updatedSession = followUp.session ?: updatedSession
            transactions.addAll(followUp.transactions)
        }
        return DebtResult.success(updatedSession, transactions)
    }

    private fun applyDebtSettlementChange(
        session: GameSession,
        debtorId: String,
        creditorId: String,
        changeAmount: Int,
        timestamp: Long,
    ): ChangeResult {
        if (changeAmount <= 0) {
            return ChangeResult(session, emptyList())
        }

        val transactions = mutableListOf<Transaction>()
        var updatedSession = session
        val updatedDebtor = updatedSession.players[debtorId]!!.copy(
            balance = updatedSession.players[debtorId]!!.balance + changeAmount,
        )
        updatedSession = updatedSession.copy(
            players = updatedSession.players + (debtorId to updatedDebtor),
        )

        if (creditorId == EntityRef.BANK) {
            val (creditTx, sessionAfterCredit) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.BANK_CREDIT,
                timestamp = timestamp,
                fromEntity = EntityRef.BANK,
                toEntity = debtorId,
                playerId = debtorId,
                amount = changeAmount,
                reversible = true,
            )
            transactions += creditTx
            updatedSession = sessionAfterCredit
        } else {
            val updatedCreditor = updatedSession.players[creditorId]!!.copy(
                balance = updatedSession.players[creditorId]!!.balance - changeAmount,
            )
            updatedSession = updatedSession.copy(
                players = updatedSession.players + (creditorId to updatedCreditor),
            )
            val (changeTx, sessionAfterChange) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.RENT_PAYMENT,
                timestamp = timestamp,
                fromEntity = creditorId,
                toEntity = debtorId,
                playerId = debtorId,
                amount = changeAmount,
                reversible = true,
            )
            transactions += changeTx
            updatedSession = sessionAfterChange
        }

        return ChangeResult(updatedSession, transactions)
    }

    private data class ChangeResult(
        val session: GameSession,
        val transactions: List<Transaction>,
    )

    private fun completeDebtReason(
        session: GameSession,
        debt: DebtResolutionState,
        debtorId: String,
        timestamp: Long,
    ): DebtResult {
        return when (debt.reason) {
            DebtReason.RENT -> {
                if (debt.propertyId == null) return DebtResult.success(session, emptyList())
                completeRentAfterDebtResolution(
                    session = session,
                    propertyId = debt.propertyId,
                    visitorId = debtorId,
                    creditorId = debt.creditorPlayerId,
                    timestamp = timestamp,
                )
            }
            DebtReason.PURCHASE -> {
                if (debt.propertyId == null) return DebtResult.success(session, emptyList())
                completePurchaseAfterDebtResolution(session, debtorId, debt.propertyId, timestamp)
            }
            DebtReason.LOCATION -> {
                if (debt.propertyId == null) return DebtResult.success(session, emptyList())
                completeLocationAfterDebtResolution(session, debtorId, debt.propertyId, timestamp)
            }
            DebtReason.JAIL -> {
                val player = session.players[debtorId]!!
                if (!player.jailStatus) return DebtResult.success(session, emptyList())
                val updated = session.copy(
                    players = session.players + (debtorId to player.copy(jailStatus = false)),
                )
                val (tx, sessionAfter) = transactionFactory.create(
                    session = updated,
                    type = TransactionType.JAIL_STATUS_CHANGE,
                    timestamp = timestamp,
                    playerId = debtorId,
                )
                DebtResult.success(sessionAfter, listOf(tx))
            }
            DebtReason.GENERIC -> DebtResult.success(session, emptyList())
        }
    }

    private fun completePurchaseAfterDebtResolution(
        session: GameSession,
        buyerId: String,
        propertyId: String,
        timestamp: Long,
    ): DebtResult {
        val propertyDef = definitions.properties[propertyId]!!
        val propertyState = session.properties[propertyId]!!
        if (propertyState.ownerPlayerId != null) {
            return DebtResult.success(session, emptyList())
        }
        val updatedProperty = propertyState.copy(ownerPlayerId = buyerId, currentRentLevel = 1)
        var updatedSession = session.copy(
            properties = session.properties + (propertyId to updatedProperty),
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.PROPERTY_PURCHASE,
            timestamp = timestamp,
            fromEntity = buyerId,
            toEntity = EntityRef.BANK,
            playerId = buyerId,
            propertyId = propertyId,
            amount = propertyDef.purchasePrice,
        )
        updatedSession = sessionAfter
        val colorSetRules = ColorSetRules(definitions, transactionFactory)
        val bonus = colorSetRules.applyCompletionBonusIfNeeded(updatedSession, propertyId, buyerId, timestamp)
        return DebtResult.success(bonus.session, listOf(tx) + bonus.transactions)
    }

    private fun completeLocationAfterDebtResolution(
        session: GameSession,
        playerId: String,
        propertyId: String,
        timestamp: Long,
    ): DebtResult {
        val propertyState = session.properties[propertyId]!!
        val transactions = mutableListOf<Transaction>()
        var updatedSession = session
        when (propertyState.ownerPlayerId) {
            null -> {
                val purchase = completePurchaseAfterDebtResolution(updatedSession, playerId, propertyId, timestamp)
                return purchase
            }
            playerId -> {
                val oldLevel = propertyState.currentRentLevel
                val newLevel = RentLevelOperations.increaseLevel(
                    oldLevel,
                    1,
                    definitions.rules.maximumRentLevel,
                )
                updatedSession = updatedSession.copy(
                    properties = updatedSession.properties + (
                        propertyId to propertyState.copy(currentRentLevel = newLevel)
                    ),
                )
                val (tx, sessionAfter) = transactionFactory.create(
                    session = updatedSession,
                    type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
                    timestamp = timestamp,
                    playerId = playerId,
                    propertyId = propertyId,
                    amount = newLevel,
                    stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
                    stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
                )
                transactions += tx
                updatedSession = sessionAfter
            }
            else -> {
                val ownerId = propertyState.ownerPlayerId!!
                val owner = updatedSession.players[ownerId]!!
                if (!owner.jailStatus) {
                    val propertyDef = definitions.properties[propertyId]!!
                    val rentAmount = RentLevelOperations.rentAmount(propertyDef, propertyState)
                    val visitor = updatedSession.players[playerId]!!
                    val updatedVisitor = visitor.copy(balance = visitor.balance - rentAmount)
                    val updatedOwner = owner.copy(balance = owner.balance + rentAmount)
                    updatedSession = updatedSession.copy(
                        players = updatedSession.players +
                            (playerId to updatedVisitor) +
                            (ownerId to updatedOwner),
                    )
                    val (rentTx, sessionAfterRent) = transactionFactory.create(
                        session = updatedSession,
                        type = TransactionType.RENT_PAYMENT,
                        timestamp = timestamp,
                        fromEntity = playerId,
                        toEntity = ownerId,
                        playerId = playerId,
                        propertyId = propertyId,
                        amount = rentAmount,
                    )
                    transactions += rentTx
                    updatedSession = sessionAfterRent
                    val newLevel = RentLevelOperations.increaseLevel(
                        propertyState.currentRentLevel,
                        1,
                        definitions.rules.maximumRentLevel,
                    )
                    updatedSession = updatedSession.copy(
                        properties = updatedSession.properties + (
                            propertyId to propertyState.copy(currentRentLevel = newLevel)
                        ),
                    )
                }
            }
        }
        return DebtResult.success(updatedSession, transactions)
    }

    fun checkBankruptcyIfCannotResolve(
        session: GameSession,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        val debt = session.debtResolution
            ?: return DebtResult.failure("No debt resolution in progress")
        val debtorId = debt.debtorPlayerId
        if (session.players[debtorId]!!.balance + totalPropertyValue(debtorId, session) < debt.amountRemaining) {
            return bankruptcyRules.declareBankruptcy(
                session,
                debtorId,
                debt.creditorPlayerId,
                debt.amountRemaining,
                timestamp,
            )
        }
        return DebtResult.success(session, emptyList())
    }

    private fun completeRentAfterDebtResolution(
        session: GameSession,
        propertyId: String,
        visitorId: String,
        creditorId: String,
        timestamp: Long,
    ): DebtResult {
        val propertyState = session.properties[propertyId]!!
        val owner = session.players[creditorId]!!
        val rules = definitions.rules
        val transactions = mutableListOf<Transaction>()
        var updatedSession = session

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
            playerId = creditorId,
            propertyId = propertyId,
            amount = newLevel,
            stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
            stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
        )
        transactions += levelTx
        updatedSession = sessionAfterLevel

        val chargeLevelOverride = RentLevelOperations.effectiveChargeLevel(
            propertyState,
            session.temporaryEffects,
        )
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

        return DebtResult.success(updatedSession, transactions)
    }

    private fun totalPropertyValue(playerId: String, session: GameSession): Int {
        val propertyValue = session.properties.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.properties[it.propertyId]!!.purchasePrice }
        val energyGridValue = session.energyGrids.values
            .filter { it.ownerPlayerId == playerId }
            .sumOf { definitions.energyGrids[it.energyGridId]!!.purchasePrice }
        return propertyValue + energyGridValue
    }

    private fun clearDebtAndMaybeReleaseJail(session: GameSession, debtorId: String): GameSession {
        val player = session.players[debtorId]!!
        val released = if (player.jailStatus && session.debtResolution?.creditorPlayerId == EntityRef.BANK) {
            player.copy(jailStatus = false)
        } else {
            player
        }
        return session.copy(
            players = session.players + (debtorId to released),
            debtResolution = null,
        )
    }

    data class DebtResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val error: String?,
    ) {
        companion object {
            fun success(session: GameSession, transactions: List<Transaction>) =
                DebtResult(session, transactions, null)

            fun failure(message: String) = DebtResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
