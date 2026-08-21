package com.boardbanker.app.gameplay.presentation

data class BalanceChangeUi(
    val playerId: String? = null,
    val playerName: String,
    val before: Int,
    val after: Int,
)

data class PropertyChangeUi(
    val propertyName: String,
    val ownerName: String? = null,
    val ownerPlayerId: String? = null,
    val rentLevelBefore: Int? = null,
    val rentLevelAfter: Int? = null,
    val rentAmount: Int? = null,
)

data class PlayerRankingUi(
    val playerId: String,
    val playerName: String,
    val wealth: Int,
    val wealthText: String,
    val bankrupt: Boolean,
)

data class GameplayResultUiModel(
    val displayCardId: String? = null,
    val title: String,
    val primaryMessage: String,
    val primaryPlayerId: String? = null,
    val primaryPlayerName: String? = null,
    val secondaryPlayerId: String? = null,
    val secondaryPlayerName: String? = null,
    val playerRankings: List<PlayerRankingUi> = emptyList(),
    val balanceChanges: List<BalanceChangeUi> = emptyList(),
    val propertyChanges: List<PropertyChangeUi> = emptyList(),
    val temporaryEffectMessage: String? = null,
    val physicalInstructions: List<String> = emptyList(),
    val lastTransactionSummary: String? = null,
    val isSuccess: Boolean = true,
    val isError: Boolean = false,
)
