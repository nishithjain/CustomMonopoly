package com.boardbanker.app.ui.screens.gameover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameOverViewModel(
    private val sessionManager: ActiveGameSessionManager,
    definitions: GameDefinitions,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val resultMapper = BankingResultMapper(definitions)

    private val _result = MutableStateFlow<GameplayResultUiModel?>(null)
    val result: StateFlow<GameplayResultUiModel?> = _result.asStateFlow()

    init {
        val session = sessionManager.currentSession()
        _result.value = if (session != null && session.status == GameStatus.FINISHED) {
            resultMapper.mapWinner(session)
        } else {
            null
        }
        gameEndAudioCoordinator.onWinnerScreenPresented(gameAudioFeedback)
    }
}

class GameOverViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameOverViewModel::class.java)) {
            return GameOverViewModel(
                sessionManager,
                definitions,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
