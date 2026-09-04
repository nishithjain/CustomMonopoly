package com.boardbanker.app.banking

import com.boardbanker.app.gameplay.presentation.BalanceChangeUi
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.presentation.PlayerRankingUi
import com.boardbanker.app.gameplay.presentation.PropertyChangeUi
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.displayNameWithNumber
import com.boardbanker.core.rules.WinnerCalculator

class BankingResultMapper(
    private val definitions: GameDefinitions,
) {
    private val winnerCalculator = WinnerCalculator(definitions)

    private fun money(amount: Int): String = formatMoney(amount, definitions)

    fun mapGoResult(result: GameResult, playerId: String, balanceBefore: Int): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val amount = definitions.bankingValues.goSalary
        return GameplayResultUiModel(
            title = "GO",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("collected ${money(amount)}\n\n")
                append("Balance:\n${money(balanceBefore)} → ${money(balanceAfter)}")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = playerId, playerName = playerName, before = balanceBefore, after = balanceAfter),
            ),
        )
    }

    fun mapLocationFeeOnlyResult(
        result: GameResult,
        playerId: String,
        balanceBefore: Int,
    ): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val fee = definitions.bankingValues.locationFee
        return GameplayResultUiModel(
            title = "LOCATION",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${money(fee)}.\n\n")
                append("Move the physical token\nto the Property you choose.\n\n")
                append("Do not collect ${money(definitions.bankingValues.goSalary)}\nif you pass GO.\n\n")
                append("Now scan the Property card.")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = playerId, playerName = playerName, before = balanceBefore, after = balanceAfter),
            ),
            physicalInstructions = listOf(
                "Scan the destination Property card on Active Game.",
            ),
        )
    }

    fun mapLocationResult(
        result: GameResult,
        playerId: String,
        propertyId: String,
        balanceBefore: Int,
    ): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val fee = definitions.bankingValues.locationFee
        return GameplayResultUiModel(
            title = "LOCATION",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${money(fee)}.\n\n")
                append("Move the physical token\nto the Property you choose.\n\n")
                append("Do not collect ${money(definitions.bankingValues.goSalary)}\nif you pass GO.")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = playerId, playerName = playerName, before = balanceBefore, after = balanceAfter),
            ),
            physicalInstructions = listOf(
                "Complete the destination action after moving the token.",
            ),
        )
    }

    fun mapJailFeeResult(result: GameResult, playerId: String, balanceBefore: Int): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val fee = definitions.bankingValues.jailReleaseFee
        return GameplayResultUiModel(
            title = "JAIL FEE PAID",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${money(fee)}.\n\n")
                append("Released from Jail.\n\n")
                append("Roll and move normally.")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = playerId, playerName = playerName, before = balanceBefore, after = balanceAfter),
            ),
        )
    }

    fun mapGoToJailResult(session: GameSession, playerId: String): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, session)
        return GameplayResultUiModel(
            title = "GO TO JAIL",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("sent to Jail.\n\n")
                append("Move the physical token\ndirectly to Jail.\n\n")
                append("Do not collect ${money(definitions.bankingValues.goSalary)}.")
            },
            physicalInstructions = listOf("Player is now in Jail."),
        )
    }

    fun mapAlreadyInJail(playerId: String, session: GameSession): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, session)
        return GameplayResultUiModel(
            title = "PLAYER ALREADY IN JAIL",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = "$playerName is already in Jail.",
            isSuccess = false,
            isError = true,
        )
    }

    fun mapNotInJail(playerId: String, session: GameSession?): GameplayResultUiModel {
        val playerName = resolvePlayerName(
            playerId,
            session ?: GameSession(gameId = "INVALID", editionId = EditionIds.LEGACY_EDITION_ID),
        )
        return GameplayResultUiModel(
            title = "PLAYER IS NOT IN JAIL",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = "$playerName does not need to get out of Jail.",
            isSuccess = false,
            isError = true,
        )
    }

    fun mapJailPassResult(playerId: String, session: GameSession): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, session)
        return GameplayResultUiModel(
            title = "GET OUT OF JAIL PASS",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("$playerName used a Get Out of Jail Pass\n")
                append("No Jail fee was charged.\n\n")
                append("Roll and move normally.")
            },
            physicalInstructions = listOf("Released from Jail."),
        )
    }

    fun mapJailDoublesRelease(playerId: String, session: GameSession): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, session)
        return GameplayResultUiModel(
            title = "RELEASED FROM JAIL",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("Use the physical dice result\nto move the token.\n\n")
                append("Complete the destination action.\n\n")
                append("Turn ends after that move.")
            },
            physicalInstructions = listOf("Released from Jail."),
        )
    }

    fun mapAuctionWin(result: GameResult, propertyId: String, winnerId: String): GameplayResultUiModel {
        val property = definitions.properties[propertyId]!!
        val winnerName = resolvePlayerName(winnerId, result.session)
        val bid = result.transactions.firstOrNull { it.transactionType == TransactionType.AUCTION_WIN }?.amount
            ?: result.session.transactions.lastOrNull { it.transactionType == TransactionType.AUCTION_WIN }?.amount
            ?: 0
        val balanceAfter = result.session.players[winnerId]!!.balance
        val balanceBefore = balanceAfter + bid
        val rentLevel = result.session.properties[propertyId]!!.currentRentLevel
        return GameplayResultUiModel(
            title = "AUCTION COMPLETE",
            primaryPlayerId = winnerId,
            primaryPlayerName = winnerName,
            primaryMessage = buildString {
                append("won:\n${property.displayNameWithNumber()}\n\n")
                append("Winning Bid:\n${money(bid)}\n\n")
                append("Balance:\n${money(balanceBefore)} → ${money(balanceAfter)}\n\n")
                append("Rent Level:\n$rentLevel\n\n")
                append("Please give the Property card\nto $winnerName.")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = winnerId, playerName = winnerName, before = balanceBefore, after = balanceAfter),
            ),
            propertyChanges = listOf(
                PropertyChangeUi(
                    propertyName = property.displayNameWithNumber(),
                    ownerName = winnerName,
                    ownerPlayerId = winnerId,
                    rentLevelAfter = rentLevel,
                ),
            ),
        )
    }

    fun mapDebtSettled(
        result: GameResult,
        propertyIds: List<String>,
        energyGridIds: List<String> = emptyList(),
        sessionBefore: GameSession,
    ): GameplayResultUiModel {
        val debt = sessionBefore.debtResolution ?: return errorResult("No debt context.")
        val debtorName = resolvePlayerName(debt.debtorPlayerId, result.session)
        val creditorName = if (debt.creditorPlayerId == EntityRef.BANK) {
            "Bank"
        } else {
            resolvePlayerName(debt.creditorPlayerId, result.session)
        }
        val assetNames = propertyIds.map { PropertyDisplayNames.displayNameWithNumber(it, definitions) } +
            energyGridIds.map { com.boardbanker.core.model.EnergyGridDisplayNames.displayNameWithNumber(it, definitions) }
        val propertySummary = when (assetNames.size) {
            0 -> "Selected assets"
            1 -> assetNames.single()
            else -> "${assetNames.size} assets"
        }
        val transferMessage = when (assetNames.size) {
            1 -> "$propertySummary was transferred to $creditorName."
            else -> "$propertySummary were transferred to $creditorName."
        }
        val changeAmount = debtSettlementChangeAmount(result, debt)
        val changeMessage = when {
            changeAmount <= 0 -> null
            debt.creditorPlayerId == EntityRef.BANK ->
                "$creditorName returned ${money(changeAmount)} change to $debtorName."
            else -> "$creditorName paid ${money(changeAmount)} change to $debtorName."
        }
        val propertyChanges = propertyIds.mapNotNull { propertyId ->
            val property = definitions.properties[propertyId] ?: return@mapNotNull null
            val rentLevel = result.session.properties[propertyId]?.currentRentLevel ?: 1
            if (debt.creditorPlayerId == EntityRef.BANK) {
                PropertyChangeUi(propertyName = property.displayNameWithNumber(), ownerName = null, rentLevelAfter = rentLevel)
            } else {
                PropertyChangeUi(
                    propertyName = property.displayNameWithNumber(),
                    ownerName = creditorName,
                    ownerPlayerId = debt.creditorPlayerId,
                    rentLevelAfter = rentLevel,
                )
            }
        } + energyGridIds.mapNotNull { energyGridId ->
            val gridName = com.boardbanker.core.model.EnergyGridDisplayNames.displayNameWithNumber(energyGridId, definitions)
            if (debt.creditorPlayerId == EntityRef.BANK) {
                PropertyChangeUi(propertyName = gridName, ownerName = null)
            } else {
                PropertyChangeUi(
                    propertyName = gridName,
                    ownerName = creditorName,
                    ownerPlayerId = debt.creditorPlayerId,
                )
            }
        }
        val balanceChanges = buildDebtSettlementBalanceChanges(
            result = result,
            debt = debt,
            debtorName = debtorName,
            creditorName = creditorName,
            sessionBefore = sessionBefore,
            changeAmount = changeAmount,
        )
        return if (debt.creditorPlayerId == EntityRef.BANK) {
            GameplayResultUiModel(
                title = "DEBT SETTLED SUCCESSFULLY",
                primaryPlayerId = debt.debtorPlayerId,
                primaryPlayerName = debtorName,
                primaryMessage = buildString {
                    append(transferMessage)
                    append("\n\n")
                    append("${assetNames.firstOrNull() ?: "Asset"} is now unowned.\n\n")
                    append("Remove the physical ownership indicator.")
                    if (changeMessage != null) {
                        append("\n\n")
                        append(changeMessage)
                    }
                },
                balanceChanges = balanceChanges,
                propertyChanges = propertyChanges,
            )
        } else {
            GameplayResultUiModel(
                title = "DEBT SETTLED SUCCESSFULLY",
                primaryPlayerId = debt.debtorPlayerId,
                primaryPlayerName = debtorName,
                secondaryPlayerId = debt.creditorPlayerId,
                secondaryPlayerName = creditorName,
                primaryMessage = buildString {
                    append(transferMessage)
                    if (changeMessage != null) {
                        append("\n\n")
                        append(changeMessage)
                    }
                    append("\n\n")
                    append("Please physically give the Property card")
                    if (assetNames.size == 1) {
                        append(" to $creditorName.")
                    } else {
                        append("s to $creditorName.")
                    }
                },
                balanceChanges = balanceChanges,
                propertyChanges = propertyChanges,
            )
        }
    }

    private fun debtSettlementChangeAmount(result: GameResult, debt: com.boardbanker.core.model.DebtResolutionState): Int {
        val changeTx = result.transactions.lastOrNull {
            when (it.transactionType) {
                TransactionType.BANK_CREDIT ->
                    it.toEntity == debt.debtorPlayerId && it.fromEntity == EntityRef.BANK
                TransactionType.RENT_PAYMENT ->
                    it.fromEntity == debt.creditorPlayerId && it.toEntity == debt.debtorPlayerId
                else -> false
            }
        }
        return changeTx?.amount ?: 0
    }

    private fun buildDebtSettlementBalanceChanges(
        result: GameResult,
        debt: com.boardbanker.core.model.DebtResolutionState,
        debtorName: String,
        creditorName: String,
        sessionBefore: GameSession,
        changeAmount: Int,
    ): List<BalanceChangeUi> {
        if (changeAmount <= 0) return emptyList()
        val debtorBefore = sessionBefore.players[debt.debtorPlayerId]!!.balance
        val debtorAfter = result.session.players[debt.debtorPlayerId]!!.balance
        val changes = mutableListOf(
            BalanceChangeUi(
                playerId = debt.debtorPlayerId,
                playerName = debtorName,
                before = debtorBefore,
                after = debtorAfter,
            ),
        )
        if (debt.creditorPlayerId != EntityRef.BANK) {
            val creditorBefore = sessionBefore.players[debt.creditorPlayerId]!!.balance
            val creditorAfter = result.session.players[debt.creditorPlayerId]!!.balance
            changes += BalanceChangeUi(
                playerId = debt.creditorPlayerId,
                playerName = creditorName,
                before = creditorBefore,
                after = creditorAfter,
            )
        }
        return changes
    }

    @Deprecated("Use mapDebtSettled(result, propertyIds, sessionBefore)")
    fun mapDebtSettled(result: GameResult, propertyId: String, sessionBefore: GameSession): GameplayResultUiModel =
        mapDebtSettled(result, propertyIds = listOf(propertyId), sessionBefore = sessionBefore)

    fun mapBankruptcy(debtorId: String, session: GameSession): GameplayResultUiModel {
        val debtorName = resolvePlayerName(debtorId, session)
        return GameplayResultUiModel(
            title = "BANKRUPTCY",
            primaryPlayerId = debtorId,
            primaryPlayerName = debtorName,
            primaryMessage = buildString {
                append("cannot pay\nthe required debt.\n\n")
                append("The game is over.")
            },
            isSuccess = false,
        )
    }

    fun mapUndoResult(result: GameResult, description: String): GameplayResultUiModel {
        return GameplayResultUiModel(
            title = "ACTION UNDONE",
            primaryMessage = description,
            lastTransactionSummary = "Undo applied.",
        )
    }

    fun mapUndoBlocked(message: String): GameplayResultUiModel {
        return GameplayResultUiModel(
            title = "THIS ACTION CANNOT BE UNDONE",
            primaryMessage = message,
            isSuccess = false,
            isError = true,
        )
    }

    fun mapWinner(session: GameSession): GameplayResultUiModel {
        val winnerId = session.winnerPlayerId ?: winnerCalculator.determineWinner(session)
        val rankings = session.players.keys
            .map { playerId ->
                val player = session.players[playerId]!!
                val wealth = if (player.bankrupt) 0 else winnerCalculator.calculateWealth(session, playerId)
                PlayerRankingUi(
                    playerId = playerId,
                    playerName = resolvePlayerName(playerId, session),
                    wealth = wealth,
                    wealthText = money(wealth),
                    bankrupt = player.bankrupt,
                )
            }
            .sortedWith(
                compareByDescending<PlayerRankingUi> { if (it.bankrupt) -1 else it.wealth }
                    .thenBy { it.playerName },
            )
        val winnerName = winnerId?.let { resolvePlayerName(it, session) } ?: "Unknown"
        val cash = winnerId?.let { session.players[it]?.balance } ?: 0
        val propertyValue = winnerId?.let {
            winnerCalculator.calculateWealth(session, it) - cash
        } ?: 0
        return GameplayResultUiModel(
            title = "WINNER",
            primaryPlayerId = winnerId,
            primaryPlayerName = winnerName,
            playerRankings = rankings,
            primaryMessage = buildString {
                append("Total Wealth:\n${money(cash + propertyValue)}\n\n")
                append("Cash:\n${money(cash)}\n\n")
                append("Property Value:\n${money(propertyValue)}")
            },
        )
    }

    fun errorResult(message: String): GameplayResultUiModel =
        GameplayResultUiModel(
            title = "ERROR",
            primaryMessage = message,
            isSuccess = false,
            isError = true,
        )

    private fun resolvePlayerName(playerId: String, session: GameSession): String =
        PlayerDisplayNames.displayName(session, playerId, definitions)
}
