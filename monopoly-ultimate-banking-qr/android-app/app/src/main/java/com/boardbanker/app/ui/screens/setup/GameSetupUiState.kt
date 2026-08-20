package com.boardbanker.app.ui.screens.setup

import com.boardbanker.core.model.GameStatus

data class PendingPlayerRegistrationUi(
    val playerId: String,
    val tokenName: String,
)

data class PendingPlayerNameEditUi(
    val playerId: String,
    val tokenName: String,
    val currentName: String,
)

data class RegisteredPlayerUi(
    val playerId: String,
    val playerName: String,
    val tokenName: String,
    val balance: Int,
)

data class GameSetupUiState(
    val loading: Boolean = true,
    val gameId: String? = null,
    val registeredPlayers: List<RegisteredPlayerUi> = emptyList(),
    val playerCount: Int = 0,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 4,
    val canStartGame: Boolean = false,
    val canAddPlayer: Boolean = true,
    val status: GameStatus = GameStatus.SETUP,
    val message: String? = null,
    val showCancelConfirm: Boolean = false,
    val pendingRegistration: PendingPlayerRegistrationUi? = null,
    val pendingNameEdit: PendingPlayerNameEditUi? = null,
)

sealed class GameSetupEvent {
    data object NavigateToGame : GameSetupEvent()
    data object NavigateHome : GameSetupEvent()
    data class PlayerRegistered(val playerName: String) : GameSetupEvent()
}
