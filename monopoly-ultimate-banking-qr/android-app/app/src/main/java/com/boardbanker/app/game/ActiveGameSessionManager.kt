package com.boardbanker.app.game

import com.boardbanker.app.persistence.CommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.util.GameIdProvider
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameEngine
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the single authoritative committed [GameSession] in the app layer.
 * Routes successful engine results through [CommittedGameSessionStore] for durable persistence.
 */
class ActiveGameSessionManager(
    private var definitions: GameDefinitions,
    private val committedStore: CommittedGameSessionStore,
    private val repository: GameSessionRepository,
    private var engine: GameEngine = DefaultGameEngine(definitions),
    private val editionResolver: ((String) -> GameDefinitions)? = null,
) {
    fun currentDefinitions(): GameDefinitions = definitions

    fun bindDefinitions(next: GameDefinitions) {
        definitions = next
        engine = DefaultGameEngine(next)
    }

    fun currentSession(): GameSession? = committedStore.currentSession()

    val committedSession: StateFlow<GameSession?> = committedStore.committedSession

    suspend fun restoreFromStorage(): SavedGameLoadResult {
        val result = committedStore.loadLatestCommitted()
        if (result is SavedGameLoadResult.Success) {
            bindEdition(result.session.editionId)
        }
        return result
    }

    suspend fun processCommand(session: GameSession, command: GameCommand): ProcessCommitResult {
        bindEdition(session.editionId)
        val result = engine.process(session, command)
        if (!result.isSuccess) {
            return ProcessCommitResult.Rejected(result)
        }
        return when (val commit = committedStore.commitGameResult(result)) {
            is CommitResult.Persisted -> ProcessCommitResult.Committed(commit.session, result)
            is CommitResult.PersistenceFailed -> ProcessCommitResult.PersistenceFailed(result, commit.reason)
            is CommitResult.NotPersisted -> ProcessCommitResult.Rejected(result)
        }
    }

    suspend fun createNewGame(): ProcessCommitResult {
        val gameId = GameIdProvider.newGameId()
        return processCommand(
            session = GameSession(gameId = gameId, editionId = definitions.editionId),
            command = GameCommand.CreateGame(gameId),
        )
    }

    suspend fun deleteCurrentGame() {
        val gameId = currentSession()?.gameId
            ?: when (val load = restoreFromStorage()) {
                is SavedGameLoadResult.Success -> load.session.gameId
                else -> return
            }
        committedStore.deleteSavedGame(gameId)
    }

    private fun bindEdition(editionId: String) {
        val id = EditionIds.normalize(editionId)
        if (id == definitions.editionId) return
        val resolved = editionResolver?.invoke(id) ?: return
        bindDefinitions(resolved)
    }

    suspend fun hasResumableGame(): Boolean =
        when (restoreFromStorage()) {
            is SavedGameLoadResult.Success -> true
            else -> false
        }
}
