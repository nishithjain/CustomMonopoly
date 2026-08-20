package com.boardbanker.app.ui.screens.debt

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel

data class DebtPropertyOption(
    val propertyId: String,
    val propertyName: String,
    val debtValue: Int,
    val selected: Boolean = false,
)

data class DebtResolutionUiState(
    val debtorPlayerId: String = "",
    val debtorName: String = "",
    val creditorPlayerId: String? = null,
    val creditorName: String = "",
    val amountDue: Int = 0,
    val availableCash: Int = 0,
    val remainingAfterCash: Int = 0,
    val properties: List<DebtPropertyOption> = emptyList(),
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
)

sealed class DebtResolutionEvent {
    data object NavigateBack : DebtResolutionEvent()
    data object NavigateToGameOver : DebtResolutionEvent()
    data object OpenPropertyScanner : DebtResolutionEvent()
}
