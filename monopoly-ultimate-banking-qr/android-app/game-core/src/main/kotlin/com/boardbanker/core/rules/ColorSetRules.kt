package com.boardbanker.core.rules

import com.boardbanker.core.model.ColorGroupState
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class ColorSetRules(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
    private val rules = definitions.rulesConfig

    fun applyCompletionBonusIfNeeded(
        session: GameSession,
        purchasedPropertyId: String,
        buyerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): BonusResult {
        val propertyDef = definitions.properties[purchasedPropertyId]!!
        val colorGroup = propertyDef.colorGroup
        val groupState = session.colorGroups[colorGroup]
            ?: ColorGroupState(colorGroup = colorGroup)

        if (groupState.completionBonusApplied) {
            return BonusResult(session, emptyList())
        }

        val groupPropertyIds = definitions.boardRelationships.colorGroups[colorGroup] ?: emptyList()
        val allOwned = groupPropertyIds.all { id ->
            session.properties[id]?.ownerPlayerId != null
        }
        if (!allOwned) {
            return BonusResult(session, emptyList())
        }

        val owners = groupPropertyIds.mapNotNull { id ->
            session.properties[id]?.ownerPlayerId
        }.distinct()
        val bonusDelta = if (owners.size == 1) {
            rules.singleOwnerColorBonus
        } else {
            rules.multiOwnerColorBonus
        }

        val levelChanges = groupPropertyIds
            .filter { session.properties[it]?.ownerPlayerId != null }
            .associateWith { id ->
                RentLevelOperations.increaseLevel(
                    session.properties[id]!!.currentRentLevel,
                    bonusDelta,
                    rules.maximumRentLevel,
                )
            }

        val updatedProperties = RentLevelOperations.applyRentLevelChanges(
            session.properties,
            levelChanges,
        )
        val updatedColorGroups = session.colorGroups + (
            colorGroup to groupState.copy(completionBonusApplied = true)
        )

        var updatedSession = session.copy(
            properties = updatedProperties,
            colorGroups = updatedColorGroups,
        )

        val (tx, sessionAfterTx) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.COLOR_SET_COMPLETION_BONUS,
            timestamp = timestamp,
            playerId = buyerId,
            propertyId = purchasedPropertyId,
            amount = bonusDelta,
        )
        updatedSession = sessionAfterTx

        return BonusResult(updatedSession, listOf(tx))
    }

    data class BonusResult(
        val session: GameSession,
        val transactions: List<Transaction>,
    )
}
