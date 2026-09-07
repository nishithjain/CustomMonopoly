package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.banking.UndoAuthorizationState
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest

sealed class AdvancedBankingStep {
    data object Hub : AdvancedBankingStep()
    data class GoConfirm(val playerId: String) : AdvancedBankingStep()
    data object LocationIntro : AdvancedBankingStep()
    data class LocationConfirmPlayer(val playerId: String) : AdvancedBankingStep()
    data class GoToJailConfirm(val playerId: String) : AdvancedBankingStep()
    data class GetOutOfJailChoice(val playerId: String) : AdvancedBankingStep()
    data class JailOptions(val playerId: String) : AdvancedBankingStep()
    data class JailDoublesConfirm(val playerId: String) : AdvancedBankingStep()
    data object UndoAuthorization : AdvancedBankingStep()
}

data class BankingHubEligibility(
    val activePlayerId: String? = null,
    val activePlayerName: String? = null,
    val activePlayerInJail: Boolean = false,
    val collectGoEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val goToJailEnabled: Boolean = false,
    val getOutOfJailEnabled: Boolean = false,
)

data class AdvancedBankingUiState(
    val step: AdvancedBankingStep = AdvancedBankingStep.Hub,
    val hubEligibility: BankingHubEligibility = BankingHubEligibility(),
    val canUndo: Boolean = false,
    val undoDescription: String? = null,
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
    val authorization: UndoAuthorizationState = UndoAuthorizationState(),
)

sealed class AdvancedBankingEvent {
    data object NavigateBack : AdvancedBankingEvent()
    data class OpenScanner(
        val request: ScanRequest,
        val onCancelled: (() -> Unit)? = null,
    ) : AdvancedBankingEvent()
    data object NavigateToDebt : AdvancedBankingEvent()
    data object NavigateToGameOver : AdvancedBankingEvent()
    data object NavigateToGameStatus : AdvancedBankingEvent()
    data object NavigateToHistory : AdvancedBankingEvent()
    data object ContinueLocationOnActiveGame : AdvancedBankingEvent()
}
