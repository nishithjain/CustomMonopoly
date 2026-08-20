package com.boardbanker.app.persistence

import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.persistence.repository.SaveSessionResult
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGameSessionRepository : GameSessionRepository {
  private val games = mutableMapOf<String, GameSession>()
    private val latestFlow = MutableStateFlow<SavedGameLoadResult>(SavedGameLoadResult.NotFound)

    var saveCallCount: Int = 0
        private set

    override suspend fun save(session: GameSession): SaveSessionResult {
        saveCallCount++
        games[session.gameId] = session
        latestFlow.value = SavedGameLoadResult.Success(session)
        return SaveSessionResult.Success(session)
    }

    override suspend fun load(gameId: String): SavedGameLoadResult =
        games[gameId]?.let { SavedGameLoadResult.Success(it) } ?: SavedGameLoadResult.NotFound

    override suspend fun loadLatestActive(): SavedGameLoadResult =
        games.values.maxByOrNull { it.transactions.size }?.let { SavedGameLoadResult.Success(it) }
            ?: SavedGameLoadResult.NotFound

    override fun observeLatestActive(): Flow<SavedGameLoadResult> = latestFlow

    override suspend fun listSavedGames(): List<GameSession> = games.values.toList()

    override suspend fun delete(gameId: String) {
        games.remove(gameId)
        if (games.isEmpty()) {
            latestFlow.value = SavedGameLoadResult.NotFound
        }
    }

    override suspend fun deleteAll() {
        games.clear()
        latestFlow.value = SavedGameLoadResult.NotFound
    }
}
