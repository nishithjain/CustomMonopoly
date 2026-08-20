package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.navigation.BankingScanContext

sealed class AdvancedBankingStep {
    data object Hub : AdvancedBankingStep()
    data object GoScanPlayer : AdvancedBankingStep()
    data class GoConfirm(val playerId: String) : AdvancedBankingStep()
    data object LocationIntro : AdvancedBankingStep()
    data object LocationScanPlayer : AdvancedBankingStep()
    data object LocationScanProperty : AdvancedBankingStep()
    data object JailScanPlayer : AdvancedBankingStep()
    data class JailOptions(val playerId: String) : AdvancedBankingStep()
    data class JailDoublesConfirm(val playerId: String) : AdvancedBankingStep()
    data object UndoConfirm : AdvancedBankingStep()
}

data class AdvancedBankingUiState(
    val step: AdvancedBankingStep = AdvancedBankingStep.Hub,
    val canUndo: Boolean = false,
    val undoDescription: String? = null,
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
)

sealed class AdvancedBankingEvent {
    data object NavigateBack : AdvancedBankingEvent()
    data class OpenScanner(val context: BankingScanContext = BankingScanContext.PLAYER) : AdvancedBankingEvent()
    data object NavigateToDebt : AdvancedBankingEvent()
    data object NavigateToGameOver : AdvancedBankingEvent()
    data object NavigateToGameStatus : AdvancedBankingEvent()
    data object NavigateToHistory : AdvancedBankingEvent()
}
