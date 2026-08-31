package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel

data class OwnedPropertyUi(
    val propertyId: String,
    val propertyName: String,
    val colorGroup: String,
    val rentLevel: Int,
    val maxRentLevel: Int,
    val currentRentText: String,
    val purchasePriceText: String,
)

sealed class PlayerDetailsStep {
    data object Hub : PlayerDetailsStep()
    data object GoConfirm : PlayerDetailsStep()
    data object LocationConfirm : PlayerDetailsStep()
    data object GoToJailConfirm : PlayerDetailsStep()
    data object JailOptions : PlayerDetailsStep()
    data object JailDoublesConfirm : PlayerDetailsStep()
}

data class PlayerDetailsUiState(
    val editionId: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val tokenName: String = "",
    val balanceText: String = "",
    val jailStatusText: String = "",
    val propertyCount: Int = 0,
    val inJail: Boolean = false,
    val ownedProperties: List<OwnedPropertyUi> = emptyList(),
    val step: PlayerDetailsStep = PlayerDetailsStep.Hub,
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val selectedPropertyId: String? = null,
)

sealed class PlayerDetailsEvent {
    data object NavigateBack : PlayerDetailsEvent()
    data object OpenPropertyScanner : PlayerDetailsEvent()
    data object NavigateToDebt : PlayerDetailsEvent()
    data object NavigateToGameOver : PlayerDetailsEvent()
    data object ContinueLocationOnActiveGame : PlayerDetailsEvent()
}
