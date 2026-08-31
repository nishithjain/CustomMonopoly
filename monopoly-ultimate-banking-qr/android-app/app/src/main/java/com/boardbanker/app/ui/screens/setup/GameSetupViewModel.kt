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
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.EditionCatalog
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
    private var activeDefinitions: GameDefinitions,
    private val createNewGame: Boolean,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
    private val editionRepository: EditionRepository,
) : ViewModel() {
    private val rules get() = activeDefinitions.rules

    fun money(amount: Int): String = com.boardbanker.app.util.formatMoney(amount, activeDefinitions)

    private val _uiState = MutableStateFlow(GameSetupUiState())
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameSetupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GameSetupEvent> = _events.asSharedFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null, catalogueError = null, editionDataError = null) }
            if (createNewGame) {
                val catalog = loadCatalogOrShowError() ?: return@launch
                val choices = catalog.editions.map { EditionChoiceUi(it.editionId, it.name) }
                val selectedId = catalog.defaultEditionId
                val selectedName = choices.first { it.editionId == selectedId }.name
                _uiState.update {
                    it.copy(
                        availableEditions = choices,
                        selectedEditionId = selectedId,
                        selectedEditionName = selectedName,
                        catalogueLoaded = true,
                    )
                }
                createGameForEdition(selectedId)
            } else {
                val existing = sessionManager.currentSession()
                if (existing != null && existing.status == GameStatus.SETUP) {
                    bindEditionDefinitions(existing.editionId)
                    val editionName = editionNameFor(existing.editionId)
                    _uiState.update {
                        it.copy(
                            selectedEditionId = existing.editionId,
                            selectedEditionName = editionName,
                            catalogueLoaded = true,
                        )
                    }
                    updateFromSession(existing, editionSelectionLocked = true)
                } else {
                    when (val load = sessionManager.restoreFromStorage()) {
                        is SavedGameLoadResult.Success -> {
                            if (load.session.status == GameStatus.SETUP) {
                                bindEditionDefinitions(load.session.editionId)
                                val editionName = editionNameFor(load.session.editionId)
                                _uiState.update {
                                    it.copy(
                                        selectedEditionId = load.session.editionId,
                                        selectedEditionName = editionName,
                                        catalogueLoaded = true,
                                    )
                                }
                                updateFromSession(load.session, editionSelectionLocked = true)
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

    fun onEditionSelected(editionId: String) {
        if (_uiState.value.editionSelectionLocked || !_uiState.value.catalogueLoaded) return
        if (editionId == _uiState.value.selectedEditionId) return
        val choice = _uiState.value.availableEditions.find { it.editionId == editionId } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    selectedEditionId = choice.editionId,
                    selectedEditionName = choice.name,
                    editionDataError = null,
                    message = null,
                )
            }
            createGameForEdition(choice.editionId)
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
            val tokenName = PlayerDisplayNames.tokenName(card.cardId, activeDefinitions)
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
        val playerDefinition = activeDefinitions.players[playerId] ?: return
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
                    updateFromSession(result.session, editionSelectionLocked = true)
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
            val state = _uiState.value
            if (!state.catalogueLoaded || state.selectedEditionId == null || state.catalogueError != null) {
                return@launch
            }
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

    private suspend fun loadCatalogOrShowError(): EditionCatalog? {
        return try {
            editionRepository.loadEditionCatalog()
        } catch (ex: Exception) {
            _uiState.update {
                it.copy(
                    loading = false,
                    catalogueLoaded = false,
                    catalogueError = ex.message ?: "Unable to load edition catalogue.",
                    canStartGame = false,
                )
            }
            null
        }
    }

    private suspend fun createGameForEdition(editionId: String) {
        if (!bindEditionDefinitions(editionId)) {
            _uiState.update {
                it.copy(
                    loading = false,
                    editionDataError = "Unable to load edition data for '$editionId'.",
                    canStartGame = false,
                )
            }
            return
        }
        when (val result = sessionManager.createNewGame(editionId)) {
            is ProcessCommitResult.Committed ->
                updateFromSession(result.session, editionSelectionLocked = result.session.players.isNotEmpty())
            is ProcessCommitResult.PersistenceFailed ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        message = "Unable to save the game.\nPlease try again.",
                        canStartGame = false,
                    )
                }
            is ProcessCommitResult.Rejected ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        editionDataError = "Unable to create a new game for '$editionId'.",
                        canStartGame = false,
                    )
                }
        }
    }

    private fun bindEditionDefinitions(editionId: String): Boolean {
        return try {
            val loaded = editionRepository.load(editionId)
            activeDefinitions = loaded
            sessionManager.bindEditionForSetup(editionId)
            true
        } catch (ex: Exception) {
            false
        }
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
                updateFromSession(result.session, editionSelectionLocked = true)
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
                        val name = PlayerDisplayNames.displayName(session, error.playerId, activeDefinitions)
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
        val name = PlayerDisplayNames.displayName(session, playerId, activeDefinitions)
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

    private fun editionNameFor(editionId: String): String {
        return try {
            editionRepository.loadManifest(editionId).name
        } catch (_: Exception) {
            editionId
        }
    }

    private fun updateFromSession(session: GameSession, editionSelectionLocked: Boolean) {
        val players = session.players.map { (playerId, playerState) ->
            RegisteredPlayerUi(
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, activeDefinitions),
                tokenName = PlayerDisplayNames.tokenName(playerId, activeDefinitions),
                balance = playerState.balance,
            )
        }
        val count = players.size
        val state = _uiState.value
        val catalogueReady = state.catalogueLoaded && state.catalogueError == null
        val editionReady = state.editionDataError == null
        val canStart = catalogueReady &&
            editionReady &&
            state.selectedEditionId != null &&
            count >= rules.minimumPlayers &&
            session.status == GameStatus.SETUP
        _uiState.update {
            it.copy(
                loading = false,
                gameId = session.gameId,
                registeredPlayers = players,
                playerCount = count,
                minPlayers = rules.minimumPlayers,
                maxPlayers = rules.maximumPlayers,
                canStartGame = canStart,
                canAddPlayer = count < rules.maximumPlayers && session.status == GameStatus.SETUP,
                status = session.status,
                editionSelectionLocked = editionSelectionLocked || count > 0,
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
    private val editionRepository: EditionRepository,
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
                editionRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
