package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.presentation.DiceGambleUiState
import com.boardbanker.app.gameplay.presentation.EventDrawUiState
import com.boardbanker.app.gameplay.presentation.PhysicalDiceConfirm
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TurnKind

data class PlayerDashboardUi(
    val playerId: String,
    val playerName: String,
    val balanceText: String,
    val propertyCount: Int = 0,
    val energyGridCount: Int = 0,
    val hasEnergyGridsInEdition: Boolean = false,
    val inJail: Boolean = false,
    val isBankrupt: Boolean = false,
    val isActiveTurn: Boolean = false,
    val statusText: String = "Active",
    val assetsSummaryLine: String = "",
    val summaryLine: String = "",
    val activeEventLines: List<String> = emptyList(),
)

data class CardPresentationUi(
    val cardTypeLabel: String,
    val title: String,
    val body: String,
    val buyAmount: Int? = null,
    val ownerPlayerId: String? = null,
    val ownerName: String? = null,
)

data class GameUiState(
    val loading: Boolean = true,
    val editionId: String = "",
    val debugPreset: Boolean = false,
    val status: GameStatus? = null,
    val players: List<PlayerDashboardUi> = emptyList(),
    val workflowState: GameplayWorkflowState = GameplayWorkflowState.Ready,
    val scanRequest: ScanRequest? = null,
    val scanPrompt: String? = null,
    val expectedCardType: CardType? = null,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
    val showAbandonConfirm: Boolean = false,
    val showEndGameConfirm: Boolean = false,
    val commandInFlight: Boolean = false,
    val gameplayLocked: Boolean = false,
    val activePlayerId: String? = null,
    val activePlayerName: String? = null,
    val turnKind: TurnKind? = null,
    val diceGamble: DiceGambleUiState? = null,
    val luckyBreakRollInProgress: Boolean = false,
    val luckyBreakPhysicalConfirm: PhysicalDiceConfirm? = null,
    val luckyBreakCompletedOutcome: com.boardbanker.app.gameplay.presentation.LuckyBreakCompletedOutcome? = null,
    val eventDraw: EventDrawUiState? = null,
    val luckyDrawScannerLaunchInProgress: Boolean = false,
    val cardPresentation: CardPresentationUi? = null,
    val activePlayerInJail: Boolean = false,
    val jailResolutionMessage: String? = null,
    val actionAvailability: ActiveGameActionAvailability = ActiveGameActionAvailability(
        scanCardEnabled = false,
        endTurnEnabled = false,
        bankActionsEnabled = false,
        getOutOfJailEnabled = false,
    ),
    val endTurnSubtitle: String? = null,
    val endTurnDisabledReason: String? = null,
    val endTurnContentDescription: String = "End active player's turn",
)

sealed class GameEvent {
    data object NavigateHome : GameEvent()
    data class OpenScanner(
        val request: ScanRequest,
        val onCancelled: (() -> Unit)? = null,
    ) : GameEvent()
    data object NavigateToBanking : GameEvent()
    data class NavigateToAuction(
        val propertyId: String? = null,
        val energyGridId: String? = null,
        val startedByPlayerId: String,
    ) : GameEvent() {
        val assetId: String = requireNotNull(propertyId ?: energyGridId) {
            "Auction navigation requires propertyId or energyGridId"
        }
    }
    data object NavigateToDebt : GameEvent()
    data object NavigateToGameOver : GameEvent()
    data class NavigateToPlayerDetails(val playerId: String) : GameEvent()
}

fun GameUiState.withScanRequest(request: ScanRequest?): GameUiState = copy(
    scanRequest = request,
    scanPrompt = request?.instruction,
    expectedCardType = request?.singleExpectedType,
)
