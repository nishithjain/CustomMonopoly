package com.boardbanker.app.persistence

import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.persistence.repository.SaveSessionResult
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession

class CommittedGameSessionStore(
    private val repository: GameSessionRepository,
) {
    private var currentCommittedSession: GameSession? = null

    fun currentSession(): GameSession? = currentCommittedSession

    suspend fun commitGameResult(result: GameResult): CommitResult {
        if (!shouldPersist(result)) {
            return CommitResult.NotPersisted(result)
        }

        val saveResult = repository.save(result.session)
        return when (saveResult) {
            is SaveSessionResult.Success -> {
                currentCommittedSession = saveResult.session
                CommitResult.Persisted(saveResult.session)
            }
            is SaveSessionResult.Failure -> CommitResult.PersistenceFailed(
                result = result,
                reason = saveResult.reason,
            )
        }
    }

    suspend fun loadLatestCommitted(): com.boardbanker.core.persistence.SavedGameLoadResult {
        val loadResult = repository.loadLatestActive()
        if (loadResult is com.boardbanker.core.persistence.SavedGameLoadResult.Success) {
            currentCommittedSession = loadResult.session
        }
        return loadResult
    }

    suspend fun deleteSavedGame(gameId: String) {
        repository.delete(gameId)
        if (currentCommittedSession?.gameId == gameId) {
            currentCommittedSession = null
        }
    }

    private fun shouldPersist(result: GameResult): Boolean {
        if (result.error != null) return false
        if (result.outcome == GameOutcome.REJECTED) return false
        return true
    }
}

sealed class CommitResult {
    data class Persisted(val session: GameSession) : CommitResult()
    data class NotPersisted(val result: GameResult) : CommitResult()
    data class PersistenceFailed(val result: GameResult, val reason: String) : CommitResult()
}
