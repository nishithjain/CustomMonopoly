package com.boardbanker.app.banking

import com.boardbanker.app.gameplay.presentation.BalanceChangeUi
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.presentation.PlayerRankingUi
import com.boardbanker.app.gameplay.presentation.PropertyChangeUi
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.WinnerCalculator

class BankingResultMapper(
    private val definitions: GameDefinitions,
) {
    private val winnerCalculator = WinnerCalculator(definitions)

    fun mapGoResult(result: GameResult, playerId: String, balanceBefore: Int): GameplayResultUiModel {
        val playerName = resolvePlayerName(playerId, result.session)
        val balanceAfter = result.session.players[playerId]!!.balance
        val amount = definitions.rulesConfig.goSalary
        return GameplayResultUiModel(
            title = "GO",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("collected ${formatMoney(amount)}\n\n")
                append("Balance:\n${formatMoney(balanceBefore)} → ${formatMoney(balanceAfter)}")
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
        val fee = definitions.rulesConfig.locationFee
        return GameplayResultUiModel(
            title = "LOCATION",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${formatMoney(fee)}.\n\n")
                append("Move the physical token\nto the Property you choose.\n\n")
                append("Do not collect ${formatMoney(definitions.rulesConfig.goSalary)}\nif you pass GO.\n\n")
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
        val fee = definitions.rulesConfig.locationFee
        return GameplayResultUiModel(
            title = "LOCATION",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${formatMoney(fee)}.\n\n")
                append("Move the physical token\nto the Property you choose.\n\n")
                append("Do not collect ${formatMoney(definitions.rulesConfig.goSalary)}\nif you pass GO.")
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
        val fee = definitions.rulesConfig.jailPaymentAmount
        return GameplayResultUiModel(
            title = "JAIL FEE PAID",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = buildString {
                append("paid ${formatMoney(fee)}.\n\n")
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
                append("Do not collect ${formatMoney(definitions.rulesConfig.goSalary)}.")
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
        val playerName = resolvePlayerName(playerId, session ?: GameSession(gameId = "INVALID"))
        return GameplayResultUiModel(
            title = "PLAYER IS NOT IN JAIL",
            primaryPlayerId = playerId,
            primaryPlayerName = playerName,
            primaryMessage = "$playerName does not need to get out of Jail.",
            isSuccess = false,
            isError = true,
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
                append("won:\n${property.name}\n\n")
                append("Winning Bid:\n${formatMoney(bid)}\n\n")
                append("Balance:\n${formatMoney(balanceBefore)} → ${formatMoney(balanceAfter)}\n\n")
                append("Rent Level:\n$rentLevel\n\n")
                append("Please give the Property card\nto $winnerName.")
            },
            balanceChanges = listOf(
                BalanceChangeUi(playerId = winnerId, playerName = winnerName, before = balanceBefore, after = balanceAfter),
            ),
            propertyChanges = listOf(
                PropertyChangeUi(
                    propertyName = property.name,
                    ownerName = winnerName,
                    ownerPlayerId = winnerId,
                    rentLevelAfter = rentLevel,
                ),
            ),
        )
    }

    fun mapDebtSettled(result: GameResult, propertyId: String, sessionBefore: GameSession): GameplayResultUiModel {
        val debt = sessionBefore.debtResolution ?: return errorResult("No debt context.")
        val property = definitions.properties[propertyId]!!
        val debtorName = resolvePlayerName(debt.debtorPlayerId, result.session)
        val creditorName = if (debt.creditorPlayerId == EntityRef.BANK) {
            "Bank"
        } else {
            resolvePlayerName(debt.creditorPlayerId, result.session)
        }
        val rentLevel = result.session.properties[propertyId]!!.currentRentLevel
        return if (debt.creditorPlayerId == EntityRef.BANK) {
            GameplayResultUiModel(
                title = "PROPERTY RETURNED TO BANK",
                primaryMessage = buildString {
                    append("${property.name} is now unowned.\n\n")
                    append("Remove the physical ownership indicator.")
                },
                propertyChanges = listOf(
                    PropertyChangeUi(propertyName = property.name, ownerName = null, rentLevelAfter = 1),
                ),
            )
        } else {
            GameplayResultUiModel(
                title = "DEBT SETTLED",
                primaryPlayerId = debt.debtorPlayerId,
                primaryPlayerName = debtorName,
                secondaryPlayerId = debt.creditorPlayerId,
                secondaryPlayerName = creditorName,
                primaryMessage = buildString {
                    append("${property.name} transferred.\n\n")
                    append("Rent Level remains:\n$rentLevel\n\n")
                    append("Please physically give\nthe Property card to $creditorName.")
                },
                propertyChanges = listOf(
                    PropertyChangeUi(
                        propertyName = property.name,
                        ownerName = creditorName,
                        ownerPlayerId = debt.creditorPlayerId,
                        rentLevelAfter = rentLevel,
                    ),
                ),
            )
        }
    }

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
                append("Total Wealth:\n${formatMoney(cash + propertyValue)}\n\n")
                append("Cash:\n${formatMoney(cash)}\n\n")
                append("Property Value:\n${formatMoney(propertyValue)}")
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
