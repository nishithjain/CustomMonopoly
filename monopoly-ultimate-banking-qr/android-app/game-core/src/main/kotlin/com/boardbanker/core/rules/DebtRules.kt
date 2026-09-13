package com.boardbanker.core.rules

import com.boardbanker.core.model.DebtPropertySettlementSnapshot
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.DebtAssetSettlementMethod
import com.boardbanker.core.model.DebtSettlementSnapshot
import com.boardbanker.core.model.EventBankDebitDebtSnapshot
import com.boardbanker.core.model.EventContributorDebtSnapshot
import com.boardbanker.core.model.EventObligation
import com.boardbanker.core.model.EventObligationStatus
import com.boardbanker.core.model.EventResolution
import com.boardbanker.core.model.EventResolutionPhase
import com.boardbanker.core.model.EventMultiRecipientDebtSnapshot
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.PendingEventMultiContributorSettlement
import com.boardbanker.core.model.EnergyGridDisplayNames
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.EnergyGridState
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.JailStatusSnapshot
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.PurchaseAssetType
import com.boardbanker.core.model.displayNameWithNumber
import com.boardbanker.core.transaction.TransactionFactory

class DebtRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val bankruptcyRules: BankruptcyRules,
) {
    private val eventTransferExecutor = EventMultiPlayerTransferExecutor(definitions, transactionFactory)

    fun enterEventMultiRecipientDebt(
        session: GameSession,
        eventId: String,
        eventName: String,
        payerPlayerId: String,
        direction: EventMultiPlayerTransferSnapshot.Direction,
        amountPerRecipient: Int,
        recipientPlayerIds: List<String>,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        if (recipientPlayerIds.isEmpty()) {
            return DebtResult.failure("No eligible recipients")
        }
        val payer = session.players[payerPlayerId]
            ?: return DebtResult.failure("Unknown payer")
        val totalDue = amountPerRecipient * recipientPlayerIds.size
        val cashAvailable = payer.balance
        if (cashAvailable >= totalDue) {
            return DebtResult.failure("Payer has sufficient funds")
        }
        val shortfall = totalDue - cashAvailable
        val eventDebt = EventMultiRecipientDebtSnapshot(
            debtId = "${session.gameId}_EVENT_DEBT_${session.transactionCounter + 1}",
            eventId = eventId,
            eventName = eventName,
            payerPlayerId = payerPlayerId,
            direction = direction,
            amountPerRecipient = amountPerRecipient,
            recipientPlayerIds = recipientPlayerIds,
            totalAmountDue = totalDue,
            cashAvailable = cashAvailable,
            shortfall = shortfall,
        )
        val resolution = EventResolution(
            resolutionId = eventDebt.debtId,
            eventId = eventId,
            actingPlayerId = payerPlayerId,
            obligations = recipientPlayerIds.map { recipientId ->
                EventObligation(
                    obligationId = "${eventDebt.debtId}_$recipientId",
                    payerId = payerPlayerId,
                    recipientId = recipientId,
                    amount = amountPerRecipient,
                    status = EventObligationStatus.AWAITING_FUNDS,
                    cashPaid = 0,
                )
            },
            phase = EventResolutionPhase.AWAITING_DEBT,
        )
        val updatedSession = session.copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = payerPlayerId,
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = shortfall,
                reason = DebtReason.EVENT,
                originalAmountDue = totalDue,
                cashAmountUsed = 0,
                eventDebt = eventDebt,
            ),
            pendingEventResolution = resolution,
            undoSnapshot = session.snapshot(),
        )
        return DebtResult.success(updatedSession, emptyList())
    }

    fun enterEventContributorDebt(
        session: GameSession,
        settlement: PendingEventMultiContributorSettlement,
        contributorPlayerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        val contributor = session.players[contributorPlayerId]
            ?: return DebtResult.failure("Unknown contributor")
        val amount = settlement.amountPerContributor
        val cashAvailable = contributor.balance
        if (cashAvailable >= amount) {
            return DebtResult.failure("Contributor has sufficient funds")
        }
        val shortfall = amount - cashAvailable
        val contributorDebt = EventContributorDebtSnapshot(
            debtId = "${session.gameId}_EVENT_CONTRIB_DEBT_${session.transactionCounter + 1}",
            settlementId = settlement.settlementId,
            eventId = settlement.eventId,
            eventName = settlement.eventName,
            recipientPlayerId = settlement.recipientPlayerId,
            contributorPlayerId = contributorPlayerId,
            contributionAmount = amount,
            cashAvailable = cashAvailable,
            shortfall = shortfall,
        )
        val obligationId = "${settlement.settlementId}_$contributorPlayerId"
        val resolution = contributorEventResolution(session, settlement).withObligation(
            EventObligation(
                obligationId = obligationId,
                payerId = contributorPlayerId,
                recipientId = settlement.recipientPlayerId,
                amount = amount,
                status = EventObligationStatus.AWAITING_FUNDS,
                cashPaid = cashAvailable,
            ),
        ).copy(phase = EventResolutionPhase.AWAITING_DEBT)
        val updatedSession = session.copy(
            pendingEventMultiContributorSettlement = settlement,
            pendingEventResolution = resolution,
            debtResolution = DebtResolutionState(
                debtorPlayerId = contributorPlayerId,
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = shortfall,
                reason = DebtReason.EVENT_CONTRIBUTOR,
                originalAmountDue = amount,
                cashAmountUsed = 0,
                eventContributorDebt = contributorDebt,
            ),
            undoSnapshot = session.undoSnapshot ?: session.snapshot(),
        )
        return DebtResult.success(updatedSession, emptyList())
    }

    fun processEventMultiContributorSettlement(
        session: GameSession,
        eventId: String,
        eventName: String,
        recipientPlayerId: String,
        amountPerContributor: Int,
        contributorPlayerIds: List<String>,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        if (contributorPlayerIds.isEmpty()) {
            return DebtResult.success(session, emptyList())
        }
        session.pendingEventResolution?.let { resolution ->
            if (resolution.isBankingRecordedFor(eventId, recipientPlayerId)) {
                return DebtResult.success(
                    session.copy(
                        pendingEventMultiContributorSettlement = null,
                        pendingEventExecution = null,
                    ),
                    emptyList(),
                )
            }
        }
        val settlement = session.pendingEventMultiContributorSettlement
            ?: PendingEventMultiContributorSettlement(
                settlementId = "${session.gameId}_EVENT_COLLECT_${session.transactionCounter + 1}",
                eventId = eventId,
                eventName = eventName,
                recipientPlayerId = recipientPlayerId,
                amountPerContributor = amountPerContributor,
                contributorPlayerIds = contributorPlayerIds,
            )
        val completed = settlement.completedTransfers.toMutableList()
        var updatedSession = session.copy(
            pendingEventMultiContributorSettlement = settlement,
            pendingEventResolution = contributorEventResolution(session, settlement),
        )
        val transactions = mutableListOf<Transaction>()

        val remaining = settlement.remainingContributorIds()
        if (remaining.isEmpty()) {
            return finalizeEventMultiContributorSettlement(updatedSession, settlement.copy(completedTransfers = completed), timestamp)
        }

        if (remaining.all { updatedSession.players[it]!!.balance >= amountPerContributor }) {
            for (contributorId in remaining) {
                val transfer = executeContributorTransfer(
                    session = updatedSession,
                    contributorId = contributorId,
                    recipientId = recipientPlayerId,
                    amount = amountPerContributor,
                    eventId = eventId,
                    actingPlayerId = recipientPlayerId,
                    timestamp = timestamp,
                )
                updatedSession = transfer.session
                transactions += transfer.transactions
                completed += transfer.transfer
            }
            return finalizeEventMultiContributorSettlement(
                updatedSession,
                settlement.copy(completedTransfers = completed),
                timestamp,
                transactions,
            )
        }

        for (contributorId in remaining) {
            val contributor = updatedSession.players[contributorId]!!
            if (contributor.balance >= amountPerContributor) {
                val transfer = executeContributorTransfer(
                    session = updatedSession,
                    contributorId = contributorId,
                    recipientId = recipientPlayerId,
                    amount = amountPerContributor,
                    eventId = eventId,
                    actingPlayerId = recipientPlayerId,
                    timestamp = timestamp,
                )
                updatedSession = transfer.session
                transactions += transfer.transactions
                completed += transfer.transfer
            } else {
                val debtResult = enterEventContributorDebt(
                    session = updatedSession.copy(
                        pendingEventMultiContributorSettlement = settlement.copy(completedTransfers = completed),
                    ),
                    settlement = settlement.copy(completedTransfers = completed),
                    contributorPlayerId = contributorId,
                    timestamp = timestamp,
                )
                if (!debtResult.isSuccess) {
                    return debtResult
                }
                return DebtResult.success(debtResult.session!!, transactions + debtResult.transactions)
            }
        }

        return finalizeEventMultiContributorSettlement(
            updatedSession,
            settlement.copy(completedTransfers = completed),
            timestamp,
            transactions,
        )
    }

    fun enterEventBankDebitDebt(
        session: GameSession,
        eventId: String,
        eventName: String,
        debtorId: String,
        amount: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): DebtResult {
        if (amount <= 0) {
            return DebtResult.failure("No payment due")
        }
        val debtor = session.players[debtorId]
            ?: return DebtResult.failure("Unknown debtor")
        val existingResolution = session.pendingEventResolution
        if (existingResolution?.eventId == eventId &&
            existingResolution.actingPlayerId == debtorId &&
            existingResolution.bankDebitObligationPaid()
        ) {
            return DebtResult.failure("Event bank payment already completed")
        }
        val cashAvailable = debtor.balance
        if (cashAvailable >= amount) {
            return DebtResult.failure("Debtor has sufficient funds")
        }
        val shortfall = amount - cashAvailable
        val debtResult = enterDebtResolution(
            session = session,
            debtorId = debtorId,
            creditorId = EntityRef.BANK,
            amount = amount,
            reason = DebtReason.GENERIC,
            timestamp = timestamp,
        )
        if (!debtResult.isSuccess) return debtResult
        val obligationId = "${session.gameId}_EVENT_OBLIGATION_${session.transactionCounter + 1}"
        val obligation = EventObligation(
            obligationId = obligationId,
            payerId = debtorId,
            recipientId = EntityRef.BANK,
            amount = amount,
            status = EventObligationStatus.AWAITING_FUNDS,
            cashPaid = cashAvailable,
        )
        val resolution = EventResolution(
            resolutionId = "${session.gameId}_EVENT_RESOLUTION_${session.transactionCounter + 1}",
            eventId = eventId,
            actingPlayerId = debtorId,
            obligations = listOf(obligation),
            phase = EventResolutionPhase.AWAITING_DEBT,
        )
        val snapshot = EventBankDebitDebtSnapshot(
            debtId = "${session.gameId}_EVENT_BANK_DEBT_${session.transactionCounter + 1}",
            obligationId = obligationId,
            eventId = eventId,
            eventName = eventName,
            debtorPlayerId = debtorId,
            totalAmountDue = amount,
            cashAvailable = cashAvailable,
            shortfall = shortfall,
        )
        val updatedSession = debtResult.session!!.copy(
            debtResolution = debtResult.session.debtResolution!!.copy(eventBankDebit = snapshot),
            pendingEventResolution = resolution,
            undoSnapshot = session.snapshot(),
        )
        return DebtResult.success(updatedSession, debtResult.transactions)
    }

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

        val cashApplied = minOf(debtor.balance, amount)
        val remaining = amount - cashApplied
        val updatedPlayers = session.players + (debtorId to debtor.copy(balance = debtor.balance - cashApplied))
        val creditorFunds = if (creditorId == EntityRef.BANK) {
            null
        } else {
            session.players[creditorId]?.balance?.plus(cashApplied)
        }
        var updatedSession = session.copy(
            players = if (creditorId == EntityRef.BANK || creditorId == debtorId) {
                updatedPlayers
            } else {
                updatedPlayers + (creditorId to session.players[creditorId]!!.copy(
                    balance = creditorFunds!!,
                ))
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = debtorId,
                creditorPlayerId = creditorId,
                amountRemaining = remaining,
                reason = reason,
                propertyId = propertyId,
                originalAmountDue = amount,
                cashAmountUsed = cashApplied,
            ),
            undoSnapshot = null,
        )

        val transactions = mutableListOf<Transaction>()
        if (cashApplied > 0) {
            val (tx, sessionAfter) = transactionFactory.create(
                session = updatedSession,
                type = if (creditorId == EntityRef.BANK) TransactionType.BANK_DEBIT else TransactionType.RENT_PAYMENT,
                timestamp = timestamp,
                fromEntity = debtorId,
                toEntity = creditorId,
                playerId = debtorId,
                amount = cashApplied,
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
        if (!session.players.containsKey(debtorId)) {
            return DebtResult.failure("Unknown debtor")
        }
        if (creditorId != EntityRef.BANK && !session.players.containsKey(creditorId)) {
            return DebtResult.failure("Unknown creditor")
        }
        val undoSnapshotBeforeSettlement = session.snapshot()
        val originalAmountDue = debt.originalAmountDue
        val cashAmountUsed = debt.cashAmountUsed

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
        val useBankFallback = creditorId != EntityRef.BANK &&
            settlement.changeAmount > 0 &&
            updatedCreditorBalance(session, creditorId) < settlement.changeAmount
        val settlementMethod = if (useBankFallback) {
            DebtAssetSettlementMethod.SELL_TO_BANK
        } else {
            DebtAssetSettlementMethod.TRANSFER_TO_CREDITOR
        }
        val transactions = mutableListOf<Transaction>()
        var updatedSession = session
        val propertyActions = mutableListOf<DebtSettlementSnapshot.PropertyAction>()

        for ((energyGridId, gridState) in selectedEnergyGrids) {
            val gridDef = definitions.energyGrids[energyGridId]
                ?: return DebtResult.failure("Unknown energy grid")
            val valuation = gridDef.purchasePrice
            val soldToBank = creditorId == EntityRef.BANK || useBankFallback
            val destination = if (soldToBank) EntityRef.BANK else creditorId
            val updatedGrid = if (!soldToBank) {
                gridState.copy(ownerPlayerId = creditorId)
            } else {
                gridState.copy(ownerPlayerId = null)
            }
            updatedSession = updatedSession.copy(
                energyGrids = updatedSession.energyGrids + (energyGridId to updatedGrid),
            )
            val gridName = EnergyGridDisplayNames.displayNameWithNumber(energyGridId, definitions)
            val (ownershipTx, sessionAfterOwnership) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE,
                timestamp = timestamp,
                fromEntity = debtorId,
                toEntity = destination,
                playerId = debtorId,
                propertyId = energyGridId,
                amount = valuation,
                stateAfter = DebtPropertySettlementSnapshot.stateAfter(
                    propertyId = energyGridId,
                    propertyName = gridName,
                    settlementValue = valuation,
                    destination = destination,
                    soldToBank = soldToBank,
                    assetKind = DebtPropertySettlementSnapshot.AssetKind.ENERGY_GRID,
                ),
            )
            propertyActions += DebtSettlementSnapshot.PropertyAction(
                propertyId = energyGridId,
                propertyName = gridName,
                settlementValue = valuation,
                destination = destination,
                soldToBank = soldToBank,
            )
            transactions += ownershipTx
            updatedSession = sessionAfterOwnership
            if ((debt.sellsEventAssetsToBank() || useBankFallback) && soldToBank) {
                val creditResult = creditEventAssetSale(
                    session = updatedSession,
                    debtorId = debtorId,
                    amount = valuation,
                    eventId = debt.eventAssetSaleEventId(),
                    timestamp = timestamp,
                )
                updatedSession = creditResult.session
                transactions += creditResult.transactions
            }
        }

        for ((propertyId, propertyState) in selectedProperties) {
            val propertyDef = definitions.properties[propertyId]
                ?: return DebtResult.failure("Unknown property")
            val valuation = propertyDef.purchasePrice
            val soldToBank = creditorId == EntityRef.BANK || useBankFallback
            val destination = if (soldToBank) EntityRef.BANK else creditorId
            val propertyName = propertyDef.displayNameWithNumber()
            if (!soldToBank) {
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
                    stateAfter = DebtPropertySettlementSnapshot.stateAfter(
                        propertyId = propertyId,
                        propertyName = propertyName,
                        settlementValue = valuation,
                        destination = destination,
                        soldToBank = soldToBank,
                        assetKind = DebtPropertySettlementSnapshot.AssetKind.PROPERTY,
                    ),
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
                    stateAfter = DebtPropertySettlementSnapshot.stateAfter(
                        propertyId = propertyId,
                        propertyName = propertyName,
                        settlementValue = valuation,
                        destination = destination,
                        soldToBank = soldToBank,
                        assetKind = DebtPropertySettlementSnapshot.AssetKind.PROPERTY,
                    ),
                )
                transactions += ownershipTx
                updatedSession = sessionAfterOwnership
            }
            propertyActions += DebtSettlementSnapshot.PropertyAction(
                propertyId = propertyId,
                propertyName = propertyName,
                settlementValue = valuation,
                destination = destination,
                soldToBank = soldToBank,
            )
            if ((debt.sellsEventAssetsToBank() || useBankFallback) && soldToBank) {
                val creditResult = creditEventAssetSale(
                    session = updatedSession,
                    debtorId = debtorId,
                    amount = valuation,
                    eventId = debt.eventAssetSaleEventId(),
                    timestamp = timestamp,
                )
                updatedSession = creditResult.session
                transactions += creditResult.transactions
            }
        }

        if (settlement.remainingDebt > 0) {
            updatedSession = updatedSession.copy(
                debtResolution = debt.copy(amountRemaining = settlement.remainingDebt),
            )
            return DebtResult.success(updatedSession, transactions)
        }

        if (debt.eventBankDebit != null) {
            return completeEventBankDebitDebt(
                session = updatedSession,
                debt = debt,
                undoSnapshotBeforeSettlement = undoSnapshotBeforeSettlement,
                timestamp = timestamp,
                existingTransactions = transactions,
            )
        }

        if (debt.reason == DebtReason.EVENT && debt.eventDebt != null) {
            return completeEventMultiRecipientDebt(
                session = updatedSession,
                debt = debt,
                undoSnapshotBeforeSettlement = undoSnapshotBeforeSettlement,
                timestamp = timestamp,
                existingTransactions = transactions,
            )
        }

        if (debt.reason == DebtReason.EVENT_CONTRIBUTOR && debt.eventContributorDebt != null) {
            return completeEventContributorDebt(
                session = updatedSession,
                debt = debt,
                undoSnapshotBeforeSettlement = undoSnapshotBeforeSettlement,
                timestamp = timestamp,
                existingTransactions = transactions,
            )
        }

        if (useBankFallback) {
            val paymentResult = payCreditorFromDebtor(
                session = updatedSession,
                debtorId = debtorId,
                creditorId = creditorId,
                amount = debt.amountRemaining,
                timestamp = timestamp,
            )
            updatedSession = paymentResult.session
            transactions += paymentResult.transactions
        }

        if (settlement.changeAmount > 0) {
            if (!useBankFallback) {
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
        val settlementMetadata = DebtSettlementSnapshot.Metadata(
            settlementId = "${updatedSession.gameId}_SETTLEMENT_${updatedSession.transactionCounter + 1}",
            debtorPlayerId = debtorId,
            creditorPlayerId = creditorId,
            originalAmountDue = originalAmountDue,
            cashAmountUsed = cashAmountUsed,
            propertyValueUsed = settlement.propertyValueApplied,
            remainingDue = 0,
            reason = debt.reason,
            propertyActions = propertyActions,
            settlementMethod = settlementMethod,
        )
        val (settlementTx, sessionAfterSettlementRecord) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.RENT_DEBT_SETTLED,
            timestamp = timestamp,
            fromEntity = debtorId,
            toEntity = creditorId,
            playerId = debtorId,
            amount = originalAmountDue,
            stateAfter = DebtSettlementSnapshot.stateAfter(settlementMetadata),
            reversible = true,
        )
        updatedSession = sessionAfterSettlementRecord
        transactions += settlementTx
        return DebtResult.success(
            session = updatedSession,
            transactions = transactions,
            settlement = DebtSettlementResult.fromMetadata(settlementMetadata),
        )
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

    private fun updatedCreditorBalance(session: GameSession, creditorId: String): Int =
        session.players[creditorId]?.balance ?: 0

    private fun payCreditorFromDebtor(
        session: GameSession,
        debtorId: String,
        creditorId: String,
        amount: Int,
        timestamp: Long,
    ): ChangeResult {
        if (amount <= 0) return ChangeResult(session, emptyList())
        val debtor = session.players[debtorId] ?: return ChangeResult(session, emptyList())
        val creditor = session.players[creditorId] ?: return ChangeResult(session, emptyList())
        val updatedSession = session.copy(
            players = session.players +
                (debtorId to debtor.copy(balance = debtor.balance - amount)) +
                (creditorId to creditor.copy(balance = creditor.balance + amount)),
        )
        val (paymentTx, sessionAfterPayment) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.RENT_PAYMENT,
            timestamp = timestamp,
            fromEntity = debtorId,
            toEntity = creditorId,
            playerId = debtorId,
            amount = amount,
            reversible = true,
        )
        return ChangeResult(sessionAfterPayment, listOf(paymentTx))
    }

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
                    stateBefore = JailStatusSnapshot.stateBefore(true),
                    stateAfter = JailStatusSnapshot.stateAfter(false),
                )
                DebtResult.success(sessionAfter, listOf(tx))
            }
            DebtReason.EVENT -> DebtResult.success(session, emptyList())
            DebtReason.EVENT_CONTRIBUTOR -> DebtResult.success(session, emptyList())
            DebtReason.GENERIC -> DebtResult.success(session, emptyList())
        }
    }

    private fun DebtReason.sellsEventAssetsToBank(): Boolean =
        this == DebtReason.EVENT || this == DebtReason.EVENT_CONTRIBUTOR

    private fun DebtResolutionState.sellsEventAssetsToBank(): Boolean =
        reason.sellsEventAssetsToBank() || eventBankDebit != null

    private fun DebtResolutionState.eventAssetSaleEventId(): String? =
        eventDebt?.eventId ?: eventContributorDebt?.eventId ?: eventBankDebit?.eventId

    private data class ContributorTransferResult(
        val session: GameSession,
        val transactions: List<Transaction>,
        val transfer: EventMultiPlayerTransferSnapshot.Transfer,
    )

    private fun executeContributorTransfer(
        session: GameSession,
        contributorId: String,
        recipientId: String,
        amount: Int,
        eventId: String,
        actingPlayerId: String,
        timestamp: Long,
    ): ContributorTransferResult {
        val contributor = session.players[contributorId]!!
        val recipient = session.players[recipientId]!!
        var updatedSession = session.copy(
            players = session.players +
                (contributorId to contributor.copy(balance = contributor.balance - amount)) +
                (recipientId to recipient.copy(balance = recipient.balance + amount)),
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_PLAYER_TRANSFER,
            timestamp = timestamp,
            fromEntity = contributorId,
            toEntity = recipientId,
            playerId = actingPlayerId,
            eventId = eventId,
            amount = amount,
            reversible = true,
        )
        return ContributorTransferResult(
            session = sessionAfter,
            transactions = listOf(tx),
            transfer = EventMultiPlayerTransferSnapshot.Transfer(
                fromPlayerId = contributorId,
                toPlayerId = recipientId,
                amount = amount,
            ),
        )
    }

    private fun finalizeEventMultiContributorSettlement(
        session: GameSession,
        settlement: PendingEventMultiContributorSettlement,
        timestamp: Long,
        priorTransactions: List<Transaction> = emptyList(),
    ): DebtResult {
        session.pendingEventResolution?.let { resolution ->
            if (resolution.isBankingRecordedFor(settlement.eventId, settlement.recipientPlayerId)) {
                return DebtResult.success(
                    session.copy(
                        pendingEventMultiContributorSettlement = null,
                        pendingEventExecution = null,
                    ),
                    priorTransactions,
                )
            }
        }
        val orderedTransfers = settlement.contributorPlayerIds.mapNotNull { contributorId ->
            settlement.completedTransfers.find { it.fromPlayerId == contributorId }
        }
        if (!settlement.allContributorsResolved()) {
            return DebtResult.failure("Not all contributors have paid")
        }
        val transactions = priorTransactions.toMutableList()
        var updatedSession = session
        val transferId = "${settlement.settlementId}_${updatedSession.transactionCounter + 1}"
        val metadata = EventMultiPlayerTransferSnapshot.Metadata(
            transferId = transferId,
            eventId = settlement.eventId,
            eventName = settlement.eventName,
            payerPlayerId = settlement.recipientPlayerId,
            direction = EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER,
            transfers = orderedTransfers,
            totalAmount = orderedTransfers.sumOf { it.amount },
        )
        val (summaryTx, sessionAfterSummary) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_MULTI_PLAYER_TRANSFER,
            timestamp = timestamp,
            fromEntity = settlement.recipientPlayerId,
            playerId = settlement.recipientPlayerId,
            eventId = settlement.eventId,
            amount = metadata.totalAmount,
            stateAfter = EventMultiPlayerTransferSnapshot.stateAfter(metadata),
            reversible = true,
        )
        updatedSession = sessionAfterSummary
        transactions += summaryTx
        val (eventTx, sessionAfterEvent) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = settlement.eventId,
            playerId = settlement.recipientPlayerId,
            reversible = true,
        )
        transactions += eventTx
        val completedResolution = completedContributorEventResolution(
            session = session,
            settlement = settlement,
        )
        updatedSession = sessionAfterEvent.copy(
            pendingEventMultiContributorSettlement = null,
            pendingEventExecution = null,
            pendingEventResolution = completedResolution,
        )
        return DebtResult.success(updatedSession, transactions)
    }

    private fun completeEventContributorDebt(
        session: GameSession,
        debt: DebtResolutionState,
        undoSnapshotBeforeSettlement: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
        existingTransactions: List<Transaction>,
    ): DebtResult {
        val contributorDebt = debt.eventContributorDebt
            ?: return DebtResult.failure("Missing contributor debt metadata")
        val settlement = session.pendingEventMultiContributorSettlement
            ?: return DebtResult.failure("Missing pending contributor settlement")
        val contributorId = contributorDebt.contributorPlayerId
        val recipientId = contributorDebt.recipientPlayerId
        val amount = contributorDebt.contributionAmount
        val contributorBalance = session.players[contributorId]?.balance
            ?: return DebtResult.failure("Unknown contributor")
        if (contributorBalance < amount) {
            return DebtResult.failure("Insufficient funds to complete contribution")
        }
        val transactions = existingTransactions.toMutableList()
        val transfer = executeContributorTransfer(
            session = session,
            contributorId = contributorId,
            recipientId = recipientId,
            amount = amount,
            eventId = contributorDebt.eventId,
            actingPlayerId = recipientId,
            timestamp = timestamp,
        )
        transactions += transfer.transactions
        var updatedCompleted = settlement.completedTransfers + transfer.transfer
        val paidObligation = EventObligation(
            obligationId = "${settlement.settlementId}_$contributorId",
            payerId = contributorId,
            recipientId = recipientId,
            amount = amount,
            status = EventObligationStatus.PAID,
            cashPaid = amount,
        )
        var updatedSession = transfer.session.copy(
            debtResolution = null,
            pendingEventMultiContributorSettlement = settlement.copy(completedTransfers = updatedCompleted),
            pendingEventResolution = contributorEventResolution(
                transfer.session,
                settlement.copy(completedTransfers = updatedCompleted),
            ).withObligation(paidObligation),
            undoSnapshot = undoSnapshotBeforeSettlement,
        )
        var remaining = settlement.contributorPlayerIds.filter { id ->
            updatedCompleted.none { it.fromPlayerId == id }
        }
        if (remaining.isEmpty()) {
            return finalizeEventMultiContributorSettlement(
                updatedSession,
                settlement.copy(completedTransfers = updatedCompleted),
                timestamp,
                transactions,
            )
        }
        if (remaining.all { updatedSession.players[it]!!.balance >= amount }) {
            return processEventMultiContributorSettlement(
                session = updatedSession,
                eventId = settlement.eventId,
                eventName = settlement.eventName,
                recipientPlayerId = settlement.recipientPlayerId,
                amountPerContributor = settlement.amountPerContributor,
                contributorPlayerIds = settlement.contributorPlayerIds,
                timestamp = timestamp,
            ).let { continuation ->
                if (!continuation.isSuccess) continuation
                else DebtResult.success(
                    continuation.session!!,
                    transactions + continuation.transactions,
                )
            }
        }
        for (nextContributorId in remaining) {
            val contributor = updatedSession.players[nextContributorId]!!
            if (contributor.balance >= amount) {
                val nextTransfer = executeContributorTransfer(
                    session = updatedSession,
                    contributorId = nextContributorId,
                    recipientId = recipientId,
                    amount = amount,
                    eventId = settlement.eventId,
                    actingPlayerId = recipientId,
                    timestamp = timestamp,
                )
                updatedSession = nextTransfer.session
                transactions += nextTransfer.transactions
                updatedCompleted = updatedCompleted + nextTransfer.transfer
                remaining = settlement.contributorPlayerIds.filter { id ->
                    updatedCompleted.none { it.fromPlayerId == id }
                }
            } else {
                val debtResult = enterEventContributorDebt(
                    session = updatedSession.copy(
                        pendingEventMultiContributorSettlement = settlement.copy(completedTransfers = updatedCompleted),
                    ),
                    settlement = settlement.copy(completedTransfers = updatedCompleted),
                    contributorPlayerId = nextContributorId,
                    timestamp = timestamp,
                )
                if (!debtResult.isSuccess) return debtResult
                return DebtResult.success(debtResult.session!!, transactions + debtResult.transactions)
            }
        }
        return finalizeEventMultiContributorSettlement(
            updatedSession,
            settlement.copy(completedTransfers = updatedCompleted),
            timestamp,
            transactions,
        )
    }

    private fun creditEventAssetSale(
        session: GameSession,
        debtorId: String,
        amount: Int,
        eventId: String?,
        timestamp: Long,
    ): ChangeResult {
        val debtor = session.players[debtorId]!!
        var updatedSession = session.copy(
            players = session.players + (debtorId to debtor.copy(balance = debtor.balance + amount)),
        )
        val (creditTx, sessionAfterCredit) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_CREDIT,
            timestamp = timestamp,
            fromEntity = EntityRef.BANK,
            toEntity = debtorId,
            playerId = debtorId,
            eventId = eventId,
            amount = amount,
            reversible = true,
        )
        return ChangeResult(sessionAfterCredit, listOf(creditTx))
    }

    private fun completeEventBankDebitDebt(
        session: GameSession,
        debt: DebtResolutionState,
        undoSnapshotBeforeSettlement: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
        existingTransactions: List<Transaction>,
    ): DebtResult {
        val bankDebit = debt.eventBankDebit
            ?: return DebtResult.failure("Missing event bank debit metadata")
        val debtorId = debt.debtorPlayerId
        val resolution = session.pendingEventResolution
        val obligation = resolution?.obligation(bankDebit.obligationId)
        if (obligation?.status == EventObligationStatus.PAID || resolution?.bankingRecorded == true) {
            return DebtResult.success(
                session.copy(
                    debtResolution = null,
                    pendingEventExecution = null,
                    undoSnapshot = undoSnapshotBeforeSettlement,
                ),
                existingTransactions,
            )
        }
        val remainingDue = debt.amountRemaining
        if (remainingDue <= 0) {
            return DebtResult.failure("No remaining bank payment due")
        }
        val debtorBalance = session.players[debtorId]?.balance
            ?: return DebtResult.failure("Unknown debtor")
        if (debtorBalance < remainingDue) {
            return DebtResult.failure("Insufficient funds to complete event payment")
        }
        val transactions = existingTransactions.toMutableList()
        val updatedDebtor = session.players[debtorId]!!.copy(balance = debtorBalance - remainingDue)
        var updatedSession = session.copy(
            players = session.players + (debtorId to updatedDebtor),
        )
        val (debitTx, sessionAfterDebit) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_DEBIT,
            timestamp = timestamp,
            fromEntity = debtorId,
            toEntity = EntityRef.BANK,
            playerId = debtorId,
            eventId = bankDebit.eventId,
            amount = remainingDue,
            reversible = true,
        )
        transactions += debitTx
        updatedSession = sessionAfterDebit
        val (eventTx, sessionAfterEvent) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = bankDebit.eventId,
            playerId = debtorId,
            reversible = true,
        )
        transactions += eventTx
        val completedResolution = (resolution ?: EventResolution(
            resolutionId = bankDebit.debtId,
            eventId = bankDebit.eventId,
            actingPlayerId = debtorId,
            obligations = listOf(
                EventObligation(
                    obligationId = bankDebit.obligationId,
                    payerId = debtorId,
                    recipientId = EntityRef.BANK,
                    amount = bankDebit.totalAmountDue,
                    status = EventObligationStatus.PAID,
                    cashPaid = debt.cashAmountUsed + remainingDue,
                ),
            ),
            phase = EventResolutionPhase.COMPLETE,
            bankingRecorded = true,
        )).copy(
            phase = EventResolutionPhase.COMPLETE,
            bankingRecorded = true,
        )
        updatedSession = sessionAfterEvent.copy(
            debtResolution = null,
            pendingEventExecution = null,
            pendingEventResolution = completedResolution,
            undoSnapshot = undoSnapshotBeforeSettlement,
        )
        return DebtResult.success(updatedSession, transactions)
    }

    private fun completeEventMultiRecipientDebt(
        session: GameSession,
        debt: DebtResolutionState,
        undoSnapshotBeforeSettlement: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
        existingTransactions: List<Transaction>,
    ): DebtResult {
        val eventDebt = debt.eventDebt
            ?: return DebtResult.failure("Missing event debt metadata")
        session.pendingEventResolution?.let { resolution ->
            if (resolution.isBankingRecordedFor(eventDebt.eventId, eventDebt.payerPlayerId)) {
                return DebtResult.success(
                    session.copy(
                        debtResolution = null,
                        pendingEventExecution = null,
                        undoSnapshot = undoSnapshotBeforeSettlement,
                    ),
                    existingTransactions,
                )
            }
        }
        val debtorId = debt.debtorPlayerId
        val debtorBalance = session.players[debtorId]?.balance
            ?: return DebtResult.failure("Unknown debtor")
        if (debtorBalance < eventDebt.totalAmountDue) {
            return DebtResult.failure("Insufficient funds to complete event payment")
        }
        val transactions = existingTransactions.toMutableList()
        var updatedSession = session
        val transferResult = eventTransferExecutor.execute(
            session = updatedSession,
            eventId = eventDebt.eventId,
            eventName = eventDebt.eventName,
            payerPlayerId = eventDebt.payerPlayerId,
            recipientPlayerIds = eventDebt.recipientPlayerIds,
            amountPerRecipient = eventDebt.amountPerRecipient,
            direction = eventDebt.direction,
            timestamp = timestamp,
            transferIdPrefix = eventDebt.debtId,
        )
        updatedSession = transferResult.session
        transactions += transferResult.transactions
        val (eventTx, sessionAfterEvent) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventDebt.eventId,
            playerId = eventDebt.payerPlayerId,
            reversible = true,
        )
        transactions += eventTx
        val completedResolution = EventResolution(
            resolutionId = eventDebt.debtId,
            eventId = eventDebt.eventId,
            actingPlayerId = eventDebt.payerPlayerId,
            obligations = eventDebt.recipientPlayerIds.map { recipientId ->
                EventObligation(
                    obligationId = "${eventDebt.debtId}_$recipientId",
                    payerId = eventDebt.payerPlayerId,
                    recipientId = recipientId,
                    amount = eventDebt.amountPerRecipient,
                    status = EventObligationStatus.PAID,
                    cashPaid = eventDebt.amountPerRecipient,
                )
            },
            phase = EventResolutionPhase.COMPLETE,
            bankingRecorded = true,
        )
        updatedSession = sessionAfterEvent.copy(
            debtResolution = null,
            pendingEventExecution = null,
            pendingEventResolution = completedResolution,
            undoSnapshot = undoSnapshotBeforeSettlement,
        )
        return DebtResult.success(updatedSession, transactions)
    }

    private fun contributorEventResolution(
        session: GameSession,
        settlement: PendingEventMultiContributorSettlement,
    ): EventResolution {
        val existing = session.pendingEventResolution
        if (existing != null &&
            existing.eventId == settlement.eventId &&
            existing.actingPlayerId == settlement.recipientPlayerId
        ) {
            return existing
        }
        return EventResolution(
            resolutionId = settlement.settlementId,
            eventId = settlement.eventId,
            actingPlayerId = settlement.recipientPlayerId,
            obligations = settlement.contributorPlayerIds.map { contributorId ->
                val paid = settlement.completedTransfers.any { it.fromPlayerId == contributorId }
                EventObligation(
                    obligationId = "${settlement.settlementId}_$contributorId",
                    payerId = contributorId,
                    recipientId = settlement.recipientPlayerId,
                    amount = settlement.amountPerContributor,
                    status = if (paid) EventObligationStatus.PAID else EventObligationStatus.PENDING,
                )
            },
            phase = EventResolutionPhase.IN_PROGRESS,
        )
    }

    private fun completedContributorEventResolution(
        session: GameSession,
        settlement: PendingEventMultiContributorSettlement,
    ): EventResolution = contributorEventResolution(session, settlement).copy(
        obligations = settlement.contributorPlayerIds.map { contributorId ->
            val skipped = contributorId in settlement.skippedContributorIds
            EventObligation(
                obligationId = "${settlement.settlementId}_$contributorId",
                payerId = contributorId,
                recipientId = settlement.recipientPlayerId,
                amount = settlement.amountPerContributor,
                status = when {
                    skipped -> EventObligationStatus.BANKRUPT
                    else -> EventObligationStatus.PAID
                },
                cashPaid = if (skipped) 0 else settlement.amountPerContributor,
            )
        },
        phase = EventResolutionPhase.COMPLETE,
        bankingRecorded = true,
    )

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
            assetName = propertyDef.displayNameWithNumber(),
            assetType = PurchaseAssetType.PROPERTY,
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
        val bankruptcyAmount = if (debt.reason == DebtReason.EVENT_CONTRIBUTOR && debt.eventContributorDebt != null) {
            debt.eventContributorDebt.contributionAmount
        } else {
            debt.amountRemaining
        }
        if (session.players[debtorId]!!.balance + totalPropertyValue(debtorId, session) < bankruptcyAmount) {
            if (debt.reason == DebtReason.EVENT_CONTRIBUTOR && debt.eventContributorDebt != null) {
                return handleContributorBankruptcy(session, debt, timestamp)
            }
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

    private fun handleContributorBankruptcy(
        session: GameSession,
        debt: DebtResolutionState,
        timestamp: Long,
    ): DebtResult {
        val contributorDebt = debt.eventContributorDebt
            ?: return DebtResult.failure("Missing contributor debt metadata")
        val settlement = session.pendingEventMultiContributorSettlement
            ?: return DebtResult.failure("Missing pending contributor settlement")
        val debtorId = debt.debtorPlayerId
        val player = session.players[debtorId]
            ?: return DebtResult.failure("Unknown contributor")
        val partialPayment = minOf(player.balance, contributorDebt.contributionAmount)
        var updatedSession = session
        val transactions = mutableListOf<Transaction>()
        var completedTransfers = settlement.completedTransfers
        if (partialPayment > 0) {
            val transfer = executeContributorTransfer(
                session = updatedSession,
                contributorId = debtorId,
                recipientId = contributorDebt.recipientPlayerId,
                amount = partialPayment,
                eventId = contributorDebt.eventId,
                actingPlayerId = contributorDebt.recipientPlayerId,
                timestamp = timestamp,
            )
            updatedSession = transfer.session
            transactions += transfer.transactions
            completedTransfers += transfer.transfer
        }
        val updatedPlayer = updatedSession.players[debtorId]!!.copy(bankrupt = true, active = false, balance = 0)
        val resolution = updatedSession.pendingEventResolution
            ?.withObligation(
                EventObligation(
                    obligationId = "${settlement.settlementId}_$debtorId",
                    payerId = debtorId,
                    recipientId = contributorDebt.recipientPlayerId,
                    amount = contributorDebt.contributionAmount,
                    status = EventObligationStatus.BANKRUPT,
                    cashPaid = partialPayment,
                ),
            )
        updatedSession = updatedSession.copy(
            players = updatedSession.players + (debtorId to updatedPlayer),
            debtResolution = null,
            pendingEventResolution = resolution,
        )
        val activePlayers = updatedSession.players.values.count { it.active && !it.bankrupt }
        if (activePlayers <= 1) {
            updatedSession = updatedSession.copy(status = GameStatus.FINISHED)
        }
        val (bankruptcyTx, sessionAfterBankruptcy) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANKRUPTCY,
            timestamp = timestamp,
            playerId = debtorId,
            eventId = contributorDebt.eventId,
            amount = contributorDebt.contributionAmount,
            stateAfter = EventContributorDebtSnapshot.bankruptcyStateAfter(
                contributorDebt.eventId,
                contributorDebt.eventName,
            ),
        )
        transactions += bankruptcyTx
        updatedSession = sessionAfterBankruptcy.copy(
            pendingEventMultiContributorSettlement = settlement.copy(
                completedTransfers = completedTransfers,
                skippedContributorIds = settlement.skippedContributorIds + debtorId,
            ),
        )
        val continuation = processEventMultiContributorSettlement(
            session = updatedSession,
            eventId = settlement.eventId,
            eventName = settlement.eventName,
            recipientPlayerId = settlement.recipientPlayerId,
            amountPerContributor = settlement.amountPerContributor,
            contributorPlayerIds = settlement.contributorPlayerIds,
            timestamp = timestamp,
        )
        if (!continuation.isSuccess) {
            return continuation
        }
        return DebtResult.success(continuation.session!!, transactions + continuation.transactions)
    }

    private fun completeRentAfterDebtResolution(
        session: GameSession,
        propertyId: String,
        visitorId: String,
        creditorId: String,
        timestamp: Long,
    ): DebtResult {
        if (definitions.energyGrids.containsKey(propertyId)) {
            return DebtResult.success(session, emptyList())
        }
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

    data class DebtSettlementResult(
        val settlementId: String,
        val debtorPlayerId: String,
        val creditorPlayerId: String,
        val originalAmountDue: Int,
        val cashAmountUsed: Int,
        val propertyValueUsed: Int,
        val remainingDue: Int,
        val reason: DebtReason,
        val propertyActions: List<DebtSettlementSnapshot.PropertyAction>,
    ) {
        companion object {
            fun fromMetadata(metadata: DebtSettlementSnapshot.Metadata) = DebtSettlementResult(
                settlementId = metadata.settlementId,
                debtorPlayerId = metadata.debtorPlayerId,
                creditorPlayerId = metadata.creditorPlayerId,
                originalAmountDue = metadata.originalAmountDue,
                cashAmountUsed = metadata.cashAmountUsed,
                propertyValueUsed = metadata.propertyValueUsed,
                remainingDue = metadata.remainingDue,
                reason = metadata.reason,
                propertyActions = metadata.propertyActions,
            )
        }
    }

    data class DebtResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val error: String?,
        val settlement: DebtSettlementResult? = null,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<Transaction>,
                settlement: DebtSettlementResult? = null,
            ) = DebtResult(session, transactions, null, settlement)

            fun failure(message: String) = DebtResult(null, emptyList(), message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
