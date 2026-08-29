package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.banking.UndoAuthorizationState
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest

sealed class AdvancedBankingStep {
    data object Hub : AdvancedBankingStep()
    data object GoScanPlayer : AdvancedBankingStep()
    data class GoConfirm(val playerId: String) : AdvancedBankingStep()
    data object LocationIntro : AdvancedBankingStep()
    data object LocationScanPlayer : AdvancedBankingStep()
    data class LocationConfirmPlayer(val playerId: String) : AdvancedBankingStep()
    data object GoToJailScanPlayer : AdvancedBankingStep()
    data class GoToJailConfirm(val playerId: String) : AdvancedBankingStep()
    data object GetOutOfJailScanPlayer : AdvancedBankingStep()
    data class JailOptions(val playerId: String) : AdvancedBankingStep()
    data class JailDoublesConfirm(val playerId: String) : AdvancedBankingStep()
    data object UndoAuthorization : AdvancedBankingStep()
}

data class AdvancedBankingUiState(
    val step: AdvancedBankingStep = AdvancedBankingStep.Hub,
    val canUndo: Boolean = false,
    val undoDescription: String? = null,
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
    val authorization: UndoAuthorizationState = UndoAuthorizationState(),
)

sealed class AdvancedBankingEvent {
    data object NavigateBack : AdvancedBankingEvent()
    data class OpenScanner(val request: ScanRequest) : AdvancedBankingEvent()
    data object NavigateToDebt : AdvancedBankingEvent()
    data object NavigateToGameOver : AdvancedBankingEvent()
    data object NavigateToGameStatus : AdvancedBankingEvent()
    data object NavigateToHistory : AdvancedBankingEvent()
    data object ContinueLocationOnActiveGame : AdvancedBankingEvent()
}
