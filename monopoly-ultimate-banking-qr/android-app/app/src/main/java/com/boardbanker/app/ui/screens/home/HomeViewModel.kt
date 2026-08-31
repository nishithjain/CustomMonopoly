package com.boardbanker.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val hasSavedGame: Boolean = false,
    val savedGameStatus: GameStatus? = null,
    val definitionsError: String? = null,
    val incompatibleEdition: SavedGameLoadResult.IncompatibleEditionVersion? = null,
)

class HomeViewModel(
    private val sessionManager: ActiveGameSessionManager,
    repository: GameSessionRepository,
    definitionsError: String?,
) : ViewModel() {
    val homeState: StateFlow<HomeUiState> = repository.observeLatestActive()
        .map { result ->
            when (result) {
                is SavedGameLoadResult.Success -> HomeUiState(
                    hasSavedGame = true,
                    savedGameStatus = result.session.status,
                    definitionsError = definitionsError,
                )
                is SavedGameLoadResult.IncompatibleEditionVersion -> HomeUiState(
                    hasSavedGame = true,
                    savedGameStatus = result.gameStatus,
                    incompatibleEdition = result,
                    definitionsError = definitionsError,
                )
                is SavedGameLoadResult.MissingEdition -> HomeUiState(
                    hasSavedGame = true,
                    definitionsError = result.reason,
                )
                is SavedGameLoadResult.SessionValidationFailed -> HomeUiState(
                    hasSavedGame = true,
                    definitionsError = "Saved ${result.editionId} game failed validation: ${result.reason}",
                )
                else -> HomeUiState(
                    hasSavedGame = false,
                    savedGameStatus = null,
                    definitionsError = definitionsError,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(definitionsError = definitionsError),
        )

    init {
        viewModelScope.launch {
            sessionManager.restoreFromStorage()
        }
    }

    fun deleteSavedGame(onDeleted: () -> Unit) {
        viewModelScope.launch {
            sessionManager.deleteCurrentGame()
            onDeleted()
        }
    }
}

class HomeViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val repository: GameSessionRepository,
    private val definitionsError: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(sessionManager, repository, definitionsError) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
