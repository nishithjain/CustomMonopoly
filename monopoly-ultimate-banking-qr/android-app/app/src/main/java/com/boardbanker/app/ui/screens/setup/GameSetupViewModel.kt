package com.boardbanker.app.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameSetupViewModel(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val createNewGame: Boolean,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val rules = definitions.rulesConfig

    private val _uiState = MutableStateFlow(GameSetupUiState())
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameSetupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GameSetupEvent> = _events.asSharedFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            if (createNewGame) {
                when (val result = sessionManager.createNewGame()) {
                    is ProcessCommitResult.Committed -> updateFromSession(result.session)
                    is ProcessCommitResult.PersistenceFailed ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                message = "Unable to save the game.\nPlease try again.",
                            )
                        }
                    is ProcessCommitResult.Rejected ->
                        _uiState.update {
                            it.copy(loading = false, message = "Unable to create a new game.")
                        }
                }
            } else {
                val existing = sessionManager.currentSession()
                if (existing != null && existing.status == GameStatus.SETUP) {
                    updateFromSession(existing)
                } else {
                    when (val load = sessionManager.restoreFromStorage()) {
                        is SavedGameLoadResult.Success -> {
                            if (load.session.status == GameStatus.SETUP) {
                                updateFromSession(load.session)
                            } else {
                                _uiState.update {
                                    it.copy(loading = false, message = "No setup game found to resume.")
                                }
                            }
                        }
                        else -> _uiState.update {
                            it.copy(loading = false, message = "No setup game found to resume.")
                        }
                    }
                }
            }
        }
    }

    fun onPlayerCardScanned(card: ResolvedCard) {
        if (card.cardType != CardType.USER) return
        viewModelScope.launch {
            val session = sessionManager.currentSession() ?: return@launch
            if (session.players.containsKey(card.cardId)) {
                showDuplicatePlayerError(session, card.cardId)
                return@launch
            }
            val tokenName = PlayerDisplayNames.tokenName(card.cardId, definitions)
            _uiState.update {
                it.copy(
                    pendingRegistration = PendingPlayerRegistrationUi(
                        playerId = card.cardId,
                        tokenName = tokenName,
                    ),
                    pendingNameEdit = null,
                    message = null,
                )
            }
        }
    }

    fun onPlayerIdScanned(playerId: String) {
        val playerDefinition = definitions.players[playerId] ?: return
        onPlayerCardScanned(
            ResolvedCard(
                cardId = playerId,
                cardType = CardType.USER,
                displayName = playerDefinition.displayName,
                qrPayload = playerDefinition.qrPayload,
            ),
        )
    }

    fun confirmPendingRegistration(rawName: String) {
        viewModelScope.launch {
            val pending = _uiState.value.pendingRegistration ?: return@launch
            val session = sessionManager.currentSession() ?: return@launch
            registerPlayer(session, pending.playerId, rawName)
        }
    }

    fun cancelPendingRegistration() {
        _uiState.update { it.copy(pendingRegistration = null) }
    }

    fun startEditPlayerName(playerId: String) {
        val registered = _uiState.value.registeredPlayers.find { it.playerId == playerId } ?: return
        _uiState.update {
            it.copy(
                pendingNameEdit = PendingPlayerNameEditUi(
                    playerId = playerId,
                    tokenName = registered.tokenName,
                    currentName = registered.playerName,
                ),
                pendingRegistration = null,
            )
        }
    }

    fun confirmEditPlayerName(rawName: String) {
        viewModelScope.launch {
            val pending = _uiState.value.pendingNameEdit ?: return@launch
            val session = sessionManager.currentSession() ?: return@launch
            when (
                val result = sessionManager.processCommand(
                    session,
                    GameCommand.RenamePlayer(pending.playerId, rawName),
                )
            ) {
                is ProcessCommitResult.Committed -> {
                    updateFromSession(result.session)
                    _uiState.update { it.copy(pendingNameEdit = null) }
                }
                is ProcessCommitResult.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        result.result.error,
                    )
                    _uiState.update {
                        it.copy(message = playerNameErrorMessage(result.result.error))
                    }
                }
                is ProcessCommitResult.PersistenceFailed ->
                    _uiState.update {
                        it.copy(message = "Unable to save the game.\nPlease try again.")
                    }
            }
        }
    }

    fun cancelEditPlayerName() {
        _uiState.update { it.copy(pendingNameEdit = null) }
    }

    fun startGame() {
        viewModelScope.launch {
            val session = sessionManager.currentSession() ?: return@launch
            when (val result = sessionManager.processCommand(session, GameCommand.StartGame)) {
                is ProcessCommitResult.Committed -> {
                    GameplayOutcomeAudio.playCommittedOutcome(
                        gameAudioFeedback,
                        result.result,
                        session,
                        CommitAudioTrigger.GameStarted,
                    )
                    gameEndAudioCoordinator.resetForNewGame()
                    _events.emit(GameSetupEvent.NavigateToGame)
                }
                is ProcessCommitResult.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update {
                        it.copy(message = "Need at least ${rules.minimumPlayers} players to start.")
                    }
                }
                is ProcessCommitResult.PersistenceFailed ->
                    _uiState.update {
                        it.copy(message = "Unable to save the game.\nPlease try again.")
                    }
            }
        }
    }

    fun requestCancelGame() {
        _uiState.update { it.copy(showCancelConfirm = true) }
    }

    fun dismissCancelConfirm() {
        _uiState.update { it.copy(showCancelConfirm = false) }
    }

    fun confirmCancelGame() {
        viewModelScope.launch {
            sessionManager.deleteCurrentGame()
            _uiState.update { it.copy(showCancelConfirm = false) }
            _events.emit(GameSetupEvent.NavigateHome)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun registerPlayer(session: GameSession, playerId: String, rawName: String) {
        when (
            val result = sessionManager.processCommand(
                session,
                GameCommand.RegisterPlayer(playerId, rawName),
            )
        ) {
            is ProcessCommitResult.Committed -> {
                val playerName = result.session.players[playerId]?.playerName ?: rawName.trim()
                updateFromSession(result.session)
                _uiState.update { it.copy(pendingRegistration = null) }
                _events.emit(GameSetupEvent.PlayerRegistered(playerName))
            }
            is ProcessCommitResult.Rejected -> {
                InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                    gameAudioFeedback,
                    result.result.error,
                )
                val message = when (val error = result.result.error) {
                    is GameError.DuplicatePlayer -> {
                        val name = PlayerDisplayNames.displayName(session, error.playerId, definitions)
                        "PLAYER ALREADY REGISTERED\n\n$name is already part of this game."
                    }
                    is GameError.PlayerLimit -> "Maximum ${rules.maximumPlayers} players reached."
                    else -> playerNameErrorMessage(result.result.error) ?: "Unable to register player."
                }
                _uiState.update { it.copy(message = message) }
            }
            is ProcessCommitResult.PersistenceFailed ->
                _uiState.update {
                    it.copy(message = "Unable to save the game.\nPlease try again.")
                }
        }
    }

    private fun showDuplicatePlayerError(session: GameSession, playerId: String) {
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            gameAudioFeedback,
            GameError.DuplicatePlayer(playerId),
        )
        val name = PlayerDisplayNames.displayName(session, playerId, definitions)
        _uiState.update {
            it.copy(
                message = "PLAYER ALREADY REGISTERED\n\n$name is already part of this game.",
                pendingRegistration = null,
            )
        }
    }

    private fun playerNameErrorMessage(error: GameError?): String? = when (error) {
        is GameError.InvalidPlayerName -> error.message
        is GameError.PlayerNameTooLong -> "Player name must be ${error.maxLength} characters or fewer."
        else -> null
    }

    private fun updateFromSession(session: GameSession) {
        val players = session.players.map { (playerId, playerState) ->
            RegisteredPlayerUi(
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, definitions),
                tokenName = PlayerDisplayNames.tokenName(playerId, definitions),
                balance = playerState.balance,
            )
        }
        val count = players.size
        _uiState.update {
            it.copy(
                loading = false,
                gameId = session.gameId,
                registeredPlayers = players,
                playerCount = count,
                minPlayers = rules.minimumPlayers,
                maxPlayers = rules.maximumPlayers,
                canStartGame = count >= rules.minimumPlayers && session.status == GameStatus.SETUP,
                canAddPlayer = count < rules.maximumPlayers && session.status == GameStatus.SETUP,
                status = session.status,
                message = null,
            )
        }
    }
}

class GameSetupViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val createNewGame: Boolean,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameSetupViewModel::class.java)) {
            return GameSetupViewModel(
                sessionManager,
                definitions,
                createNewGame,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
