package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel

data class OwnedPropertyUi(
    val propertyId: String,
    val propertyName: String,
    val colorGroup: String,
    val colorGroupLabel: String,
    val rentLevel: Int,
    val maxRentLevel: Int,
    val currentRentText: String,
    val purchasePriceText: String,
)

data class OwnedEnergyGridUi(
    val energyGridId: String,
    val energyGridName: String,
    val boardPosition: Int?,
    val boardPositionLabel: String?,
    val categoryLabel: String,
    val currentRentText: String,
    val purchasePriceText: String,
    val rentTierText: String?,
)

sealed class PlayerDetailsStep {
    data object Hub : PlayerDetailsStep()
    data object GoConfirm : PlayerDetailsStep()
    data object LocationConfirm : PlayerDetailsStep()
    data object GoToJailConfirm : PlayerDetailsStep()
    data object GetOutOfJailChoice : PlayerDetailsStep()
    data object JailOptions : PlayerDetailsStep()
    data object JailDoublesConfirm : PlayerDetailsStep()
}

data class PlayerDetailsUiState(
    val editionId: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val tokenName: String = "",
    val balanceText: String = "",
    val playerStatusText: String = "Active",
    val propertyCount: Int = 0,
    val energyGridCount: Int = 0,
    val totalAssetCount: Int = 0,
    val isActiveTurn: Boolean = false,
    val hasEnergyGridsInEdition: Boolean = false,
    val inJail: Boolean = false,
    val jailPassCount: Int = 0,
    val ownedProperties: List<OwnedPropertyUi> = emptyList(),
    val ownedEnergyGrids: List<OwnedEnergyGridUi> = emptyList(),
    val step: PlayerDetailsStep = PlayerDetailsStep.Hub,
    val commandInFlight: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val selectedPropertyId: String? = null,
    val selectedEnergyGridId: String? = null,
)

sealed class PlayerDetailsEvent {
    data object NavigateBack : PlayerDetailsEvent()
    data object OpenPropertyScanner : PlayerDetailsEvent()
    data class OpenJailPassScanner(val request: com.boardbanker.app.scanner.ScanRequest) : PlayerDetailsEvent()
    data object NavigateToDebt : PlayerDetailsEvent()
    data object NavigateToGameOver : PlayerDetailsEvent()
    data object ContinueLocationOnActiveGame : PlayerDetailsEvent()
}
