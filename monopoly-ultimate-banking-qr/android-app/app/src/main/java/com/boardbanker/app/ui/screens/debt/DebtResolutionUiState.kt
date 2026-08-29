package com.boardbanker.app.ui.screens.debt

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel

data class DebtPropertyOption(
    val propertyId: String,
    val propertyName: String,
    val debtValue: Int,
)

data class DebtResolutionUiState(
    val debtorPlayerId: String = "",
    val debtorName: String = "",
    val creditorPlayerId: String? = null,
    val creditorName: String = "",
    val amountDue: Int = 0,
    val availableCash: Int = 0,
    val remainingAfterCash: Int = 0,
    val outstandingAmount: Int = 0,
    val selectedPropertyIds: Set<String> = emptySet(),
    val selectedPropertyCount: Int = 0,
    val selectedPropertyValue: Int = 0,
    val remainingDue: Int = 0,
    val changeAmount: Int = 0,
    val settlementSummary: DebtSettlementSummary = DebtSettlementSummary.compute(
        outstandingAmount = 0,
        selectedPropertyIds = emptySet(),
        properties = emptyList(),
        debtorName = "",
        creditorPlayerId = null,
        creditorName = "",
        formatMoney = { amount -> amount.toString() },
    ),
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
