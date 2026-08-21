package com.boardbanker.app.gameplay.presentation

import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.error.GameError
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType

class GameplayResultMapper(
    private val definitions: GameDefinitions,
) {
    fun mapPurchaseResult(
        result: GameResult,
        playerId: String,
        propertyId: String,
        balanceBefore: Int,
    ): GameplayResultUiModel {
        if (result.outcome == GameOutcome.DEBT_RESOLUTION_REQUIRED) {
            return insufficientFunds("Unable to complete purchase.")
        }
        if (!result.isSuccess) {
            return errorResult(result.error)
        }
        val property = definitions.properties[propertyId]!!
        val resolvePlayerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val rentLevel = result.session.properties[propertyId]!!.currentRentLevel
        val rentAmount = property.rentLevels.firstOrNull { it.level == rentLevel }?.amount
        val colorBonus = result.transactions
            .filter { it.transactionType == TransactionType.COLOR_SET_COMPLETION_BONUS }
        val bonusMessage = if (colorBonus.isNotEmpty()) {
            val affected = colorBonus.mapNotNull { definitions.properties[it.propertyId]?.name }
            "COLOR SET COMPLETED\n\n${property.colorGroup} group completed.\n\n" +
                affected.joinToString("\n") { "• $it" }
        } else {
            null
        }
        return GameplayResultUiModel(
            displayCardId = propertyId,
            title = "PURCHASE COMPLETE",
            primaryPlayerId = playerId,
            primaryPlayerName = resolvePlayerName,
            primaryMessage = buildString {
                append("bought\n${property.name}\n\n")
                append("Paid: M${property.purchasePrice}\n")
                append("Balance: M$balanceAfter\n")
                append("Rent: Level $rentLevel — M$rentAmount")
                if (bonusMessage != null) append("\n\n$bonusMessage")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = playerId, playerName = resolvePlayerName, before = balanceBefore, after = balanceAfter),
            ),
            propertyChanges = listOf(
                PropertyChangeUi(
                    propertyName = property.name,
                    ownerName = resolvePlayerName,
                    ownerPlayerId = playerId,
                    rentLevelAfter = rentLevel,
                    rentAmount = rentAmount,
                ),
            ),
            lastTransactionSummary = summarizeLastTransaction(result),
        )
    }

    fun mapPropertyLandingResult(
        result: GameResult,
        playerId: String,
        propertyId: String,
        sessionBefore: GameSession,
    ): GameplayResultUiModel {
        if (result.outcome == GameOutcome.DEBT_RESOLUTION_REQUIRED) {
            return insufficientFunds("Unable to complete rent payment.")
        }
        if (!result.isSuccess) {
            return errorResult(result.error)
        }
        val property = definitions.properties[propertyId]!!
        val propertyStateBefore = sessionBefore.properties[propertyId]!!
        val propertyStateAfter = result.session.properties[propertyId]!!
        val ownerId = propertyStateBefore.ownerPlayerId
        val ownerName = ownerId?.let { resolvePlayerName(it, result.session) }
        val visitorName = resolvePlayerName(playerId, result.session)
        val rentTx = result.transactions.firstOrNull { it.transactionType == TransactionType.RENT_PAYMENT }
        val levelBefore = propertyStateBefore.currentRentLevel
        val levelAfter = propertyStateAfter.currentRentLevel
        val rentAmount = property.rentLevels.firstOrNull { it.level == levelBefore }?.amount

        if (ownerId == playerId) {
            val rentAfter = property.rentLevels.firstOrNull { it.level == levelAfter }?.amount
            val title = if (levelAfter >= property.maximumRentLevel) "MAXIMUM RENT LEVEL" else "YOUR PROPERTY"
            return GameplayResultUiModel(
                displayCardId = propertyId,
                title = title,
                primaryPlayerId = ownerId,
                primaryPlayerName = ownerName,
                primaryMessage = buildString {
                    append("${property.name}\n\n")
                    append("Rent Level:\n$levelBefore → $levelAfter\n\n")
                    append("New Rent: M$rentAfter")
                },
                propertyChanges = listOf(
                    PropertyChangeUi(
                        propertyName = property.name,
                        ownerName = ownerName,
                        ownerPlayerId = ownerId,
                        rentLevelBefore = levelBefore,
                        rentLevelAfter = levelAfter,
                        rentAmount = rentAfter,
                    ),
                ),
                lastTransactionSummary = summarizeLastTransaction(result),
            )
        }

        if (ownerId != null && sessionBefore.players[ownerId]?.jailStatus == true) {
            return GameplayResultUiModel(
                displayCardId = propertyId,
                title = "NO RENT DUE",
                primaryPlayerId = ownerId,
                primaryPlayerName = ownerName,
                primaryMessage = buildString {
                    append("owns this property\n")
                    append("but is currently in Jail.\n\n")
                    append("No rent collected.\n")
                    append("Rent level unchanged.")
                },
                propertyChanges = listOf(
                    PropertyChangeUi(
                        propertyName = property.name,
                        ownerName = ownerName,
                        ownerPlayerId = ownerId,
                        rentLevelBefore = levelBefore,
                        rentLevelAfter = levelBefore,
                    ),
                ),
            )
        }

        val evt13Message = evt13OverrideMessage(sessionBefore, result.session, levelBefore, rentTx?.amount)
        val payerBalanceBefore = sessionBefore.players[playerId]!!.balance
        val payerBalanceAfter = result.session.players[playerId]!!.balance
        val ownerBalanceBefore = ownerId?.let { sessionBefore.players[it]!!.balance }
        val ownerBalanceAfter = ownerId?.let { result.session.players[it]!!.balance }
        val rentAfter = property.rentLevels.firstOrNull { it.level == levelAfter }?.amount

        return GameplayResultUiModel(
            displayCardId = propertyId,
            title = "RENT PAID",
            primaryPlayerId = playerId,
            primaryPlayerName = visitorName,
            secondaryPlayerId = ownerId,
            secondaryPlayerName = ownerName,
            primaryMessage = buildString {
                append("M${rentTx?.amount ?: rentAmount}\n\n")
                append("${property.name}\n\n")
                append("Rent Level:\n$levelBefore → $levelAfter\n\n")
                append("New Rent: M$rentAfter")
                if (evt13Message != null) append("\n\n$evt13Message")
            },
            balanceChanges = buildList {
                add(
                    BalanceChangeUi(
                        playerId = playerId,
                        playerName = visitorName,
                        before = payerBalanceBefore,
                        after = payerBalanceAfter,
                    ),
                )
                if (ownerId != null && ownerBalanceBefore != null && ownerBalanceAfter != null) {
                    add(
                        BalanceChangeUi(
                            playerId = ownerId,
                            playerName = ownerName!!,
                            before = ownerBalanceBefore,
                            after = ownerBalanceAfter,
                        ),
                    )
                }
            },
            propertyChanges = listOf(
                PropertyChangeUi(
                    propertyName = property.name,
                    ownerName = ownerName,
                    ownerPlayerId = ownerId,
                    rentLevelBefore = levelBefore,
                    rentLevelAfter = levelAfter,
                    rentAmount = rentAfter,
                ),
            ),
            temporaryEffectMessage = evt13Message,
            lastTransactionSummary = summarizeLastTransaction(result),
        )
    }

    fun mapEventResult(result: GameResult, eventId: String): GameplayResultUiModel {
        if (result.outcome == GameOutcome.DEBT_RESOLUTION_REQUIRED) {
            return insufficientFunds("Debt resolution will be available through Advanced Banking.")
        }
        if (!result.isSuccess) {
            return errorResult(result.error)
        }
        val event = definitions.events[eventId]!!
        val propertyChanges = result.transactions
            .filter { it.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE }
            .mapNotNull { tx ->
                val property = definitions.properties[tx.propertyId ?: return@mapNotNull null] ?: return@mapNotNull null
                val afterLevel = result.session.properties[property.propertyId]?.currentRentLevel
                PropertyChangeUi(
                    propertyName = property.name,
                    rentLevelAfter = afterLevel,
                )
            }
        val swapMessage = if (result.transactions.any { it.transactionType == TransactionType.PROPERTY_SWAP }) {
            "\n\nPlease physically exchange the Property cards."
        } else {
            ""
        }
        val evt13Created = result.transactions.any {
            it.transactionType == TransactionType.TEMPORARY_EFFECT_CREATED
        }
        val temporaryMessage = if (evt13Created) {
            "ON THE RUN ACTIVE\n\nThe next 2 rent payments will use Level 1 rent."
        } else {
            null
        }
        val physical = result.physicalActions.map { it.instruction }
        val gridlockMessage = if (eventId == "EVT_21") {
            "TOTAL GRIDLOCK\n\nMove all players who are not in Jail directly to Free Parking.\n\n" +
                "Do not collect ${formatMoney(definitions.bankingValues.goSalary, definitions)} for passing GO.\n\nPlayers already in Jail remain there."
        } else {
            null
        }
        return GameplayResultUiModel(
            displayCardId = eventId,
            title = if (eventId == "EVT_21") "TOTAL GRIDLOCK" else "EVENT APPLIED",
            primaryMessage = buildString {
                append("${event.name}\n\n")
                append(event.displayText().ifBlank { result.pendingMessage ?: "Event applied." })
                append(swapMessage)
                if (gridlockMessage != null) append("\n\n$gridlockMessage")
            },
            propertyChanges = propertyChanges,
            temporaryEffectMessage = temporaryMessage,
            physicalInstructions = physical,
            lastTransactionSummary = summarizeLastTransaction(result),
        )
    }

    fun mapPlayerInfo(playerId: String, session: GameSession): GameplayResultUiModel {
        val player = session.players[playerId]!!
        val ownedCount = session.properties.values.count { it.ownerPlayerId == playerId }
        return GameplayResultUiModel(
            displayCardId = playerId,
            title = "PLAYER",
            primaryPlayerId = playerId,
            primaryPlayerName = resolvePlayerName(playerId, session),
            primaryMessage = buildString {
                append("Balance: M${player.balance}\n\n")
                append("Properties: $ownedCount\n\n")
                append("Jail: ${if (player.jailStatus) "Yes" else "No"}")
            },
            isSuccess = true,
        )
    }

    fun errorResult(error: GameError?): GameplayResultUiModel {
        val message = when (error) {
            is GameError.EventError -> error.message
            is GameError.Validation -> error.message
            is GameError.InvalidState -> error.message
            is GameError.NotFound -> "${error.entity} not found: ${error.id}"
            is GameError.InsufficientFunds ->
                "INSUFFICIENT FUNDS\n\nDebt resolution will be available through Advanced Banking."
            else -> "Unable to complete action."
        }
        return GameplayResultUiModel(
            title = if (error is GameError.InsufficientFunds) "INSUFFICIENT FUNDS" else "ERROR",
            primaryMessage = message,
            isSuccess = false,
            isError = true,
        )
    }

    private fun insufficientFunds(message: String) = GameplayResultUiModel(
        title = "INSUFFICIENT FUNDS",
        primaryMessage = "$message\n\nDebt resolution will be available through Advanced Banking.",
        isSuccess = false,
        isError = true,
    )

    private fun evt13OverrideMessage(
        before: GameSession,
        after: GameSession,
        chargedLevel: Int,
        chargedAmount: Int?,
    ): String? {
        val effectBefore = before.temporaryEffects.firstOrNull {
            it.effectType == "FORCE_LEVEL_1_RENT" && it.active
        } ?: return null
        val effectAfter = after.temporaryEffects.firstOrNull {
            it.effectType == "FORCE_LEVEL_1_RENT" && it.active
        }
        val remaining = effectAfter?.remainingUses
        return buildString {
            append("EVENT EFFECT ACTIVE\n\n")
            append("Level 1 rent applies.\n\n")
            append("Normal Property Level: $chargedLevel\n\n")
            append("Rent charged: M${chargedAmount ?: "?"}\n\n")
            if (remaining != null) {
                append("Temporary effect remaining: $remaining rent payment(s)")
            } else {
                append("ON THE RUN effect ended.")
            }
        }
    }

    private fun summarizeLastTransaction(result: GameResult): String? {
        val tx = result.transactions.lastOrNull() ?: return null
        val from = tx.fromEntity?.let { entityName(it, result.session) }
        val to = tx.toEntity?.let { entityName(it, result.session) }
        val amount = tx.amount?.let { "M$it" } ?: ""
        return buildString {
            append("Last Transaction\n\n")
            append("${tx.transactionType.name.replace('_', ' ')}\n")
            if (from != null && to != null) append("$from → $to\n")
            append(amount)
        }
    }

    private fun entityName(entity: String, session: GameSession): String =
        if (entity == EntityRef.BANK) {
            "Bank"
        } else {
            PlayerDisplayNames.displayName(session, entity, definitions)
        }

    private fun resolvePlayerName(playerId: String, session: GameSession): String =
        PlayerDisplayNames.displayName(session, playerId, definitions)
}
