package com.boardbanker.app.ui.screens.persistence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.util.GameIdProvider
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PersistenceDebugViewModel(
    private val definitions: GameDefinitions,
    private val repository: GameSessionRepository,
    private val committedStore: CommittedGameSessionStore,
) : ViewModel() {
    private val engine = DefaultGameEngine(definitions)
    private var session: GameSession? = null

    private val _message = MutableStateFlow("Ready.")
    val message: StateFlow<String> = _message.asStateFlow()

    fun createTestSession() {
        val gameId = GameIdProvider.newGameId()
        var result = engine.process(GameSession(gameId = gameId), GameCommand.CreateGame(gameId))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = engine.process(result.session, GameCommand.StartGame)
        session = result.session
        _message.value = "Created test session ${result.session.gameId} with ${result.session.players.size} players."
    }

    fun saveSession() {
        val current = session ?: run {
            _message.value = "Create a test session first."
            return
        }
        viewModelScope.launch {
            val commit = committedStore.commitGameResult(
                com.boardbanker.core.engine.GameResult(session = current),
            )
            _message.value = when (commit) {
                is com.boardbanker.app.persistence.CommitResult.Persisted ->
                    "Saved session ${commit.session.gameId}."
                is com.boardbanker.app.persistence.CommitResult.NotPersisted ->
                    "Save skipped: result not committed."
                is com.boardbanker.app.persistence.CommitResult.PersistenceFailed ->
                    "Save failed: ${commit.reason}"
            }
        }
    }

    fun loadSession() {
        viewModelScope.launch {
            when (val result = repository.loadLatestActive()) {
                is SavedGameLoadResult.Success -> {
                    session = result.session
                    _message.value = "Loaded session ${result.session.gameId} (${result.session.transactions.size} transactions)."
                }
                is SavedGameLoadResult.NotFound -> _message.value = "No saved session found."
                is SavedGameLoadResult.Corrupted -> _message.value = "Corrupted save: ${result.reason}"
                is SavedGameLoadResult.IncompatibleVersion ->
                    _message.value = "Incompatible version ${result.found}."
            }
        }
    }

    fun deleteSession() {
        val gameId = session?.gameId ?: run {
            _message.value = "No session to delete."
            return
        }
        viewModelScope.launch {
            repository.delete(gameId)
            session = null
            _message.value = "Deleted session $gameId."
        }
    }
}

class PersistenceDebugViewModelFactory(
    private val definitions: GameDefinitions,
    private val repository: GameSessionRepository,
    private val committedStore: CommittedGameSessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersistenceDebugViewModel::class.java)) {
            return PersistenceDebugViewModel(definitions, repository, committedStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
