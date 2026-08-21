package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.GameStatus

data class PlayerDashboardUi(
    val playerId: String,
    val playerName: String,
    val balanceText: String,
    val propertyCount: Int = 0,
    val inJail: Boolean = false,
    val summaryLine: String = "",
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
    val status: GameStatus? = null,
    val players: List<PlayerDashboardUi> = emptyList(),
    val workflowState: GameplayWorkflowState = GameplayWorkflowState.Ready,
    val scanPrompt: String? = null,
    val expectedCardType: CardType? = null,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
    val showAbandonConfirm: Boolean = false,
    val commandInFlight: Boolean = false,
    val activeEventMessage: String? = null,
    val gameplayLocked: Boolean = false,
    val cardPresentation: CardPresentationUi? = null,
)

sealed class GameEvent {
    data object NavigateHome : GameEvent()
    data class OpenScanner(val expectedCardType: CardType?) : GameEvent()
    data object NavigateToBanking : GameEvent()
    data class NavigateToAuction(val propertyId: String, val startedByPlayerId: String) : GameEvent()
    data object NavigateToDebt : GameEvent()
    data object NavigateToGameOver : GameEvent()
    data class NavigateToPlayerDetails(val playerId: String) : GameEvent()
}
