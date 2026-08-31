package com.boardbanker.app.ui.screens.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.persistence.SavedGameLoadResult
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResumeGameViewModel(
    private val repository: GameSessionRepository,
    private val committedStore: CommittedGameSessionStore,
    private val definitions: com.boardbanker.core.model.GameDefinitions,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ResumeUiState>(ResumeUiState.Loading)
    val uiState: StateFlow<ResumeUiState> = _uiState.asStateFlow()

    private var loadedSession: com.boardbanker.core.model.GameSession? = null
    private var lastSavedMillis: Long = 0L

    fun loadSavedGame() {
        viewModelScope.launch {
            _uiState.value = ResumeUiState.Loading
            when (val result = repository.loadLatestActive()) {
                is SavedGameLoadResult.Success -> {
                    loadedSession = result.session
                    lastSavedMillis = result.session.transactions.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                    val players = result.session.players.keys.sorted().map { playerId ->
                        ResumePlayerUi(
                            playerId = playerId,
                            playerName = PlayerDisplayNames.displayName(result.session, playerId, definitions),
                        )
                    }
                    _uiState.value = ResumeUiState.Found(
                        playerCount = result.session.players.size,
                        players = players,
                        transactionCount = result.session.transactions.size,
                        status = result.session.status.name,
                        lastSavedText = DateFormat.getDateTimeInstance().format(Date(lastSavedMillis)),
                    )
                }
                is SavedGameLoadResult.NotFound -> {
                    _uiState.value = ResumeUiState.Error("No saved game found.")
                }
                is SavedGameLoadResult.Corrupted -> {
                    _uiState.value = ResumeUiState.Error("Saved game is corrupted: ${result.reason}")
                }
                is SavedGameLoadResult.IncompatibleVersion -> {
                    _uiState.value = ResumeUiState.Error(
                        "Unsupported save version ${result.found} (supported ${result.supported}).",
                    )
                }
                is SavedGameLoadResult.IncompatibleEditionVersion -> {
                    _uiState.value = ResumeUiState.Error(result.userMessage())
                }
                is SavedGameLoadResult.MissingEdition -> {
                    _uiState.value = ResumeUiState.Error(
                        "Saved game edition '${result.editionId}' is not available: ${result.reason}",
                    )
                }
                is SavedGameLoadResult.SessionValidationFailed -> {
                    _uiState.value = ResumeUiState.Error(
                        "Saved ${result.editionId} game failed validation: ${result.reason}",
                    )
                }
            }
        }
    }

    fun loadIntoStore() {
        viewModelScope.launch {
            committedStore.loadLatestCommitted()
        }
    }

    fun deleteSave(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val gameId = loadedSession?.gameId ?: return@launch
            repository.delete(gameId)
            _uiState.value = ResumeUiState.Deleted
            onDeleted()
        }
    }
}

data class ResumePlayerUi(
    val playerId: String,
    val playerName: String,
)

sealed class ResumeUiState {
    data object Loading : ResumeUiState()
    data class Found(
        val playerCount: Int,
        val players: List<ResumePlayerUi>,
        val transactionCount: Int,
        val status: String,
        val lastSavedText: String,
    ) : ResumeUiState()
    data class Error(val message: String) : ResumeUiState()
    data object Deleted : ResumeUiState()
}
