package com.boardbanker.app.persistence.repository

import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.Flow

interface GameSessionRepository {
    suspend fun save(session: GameSession): SaveSessionResult
    suspend fun load(gameId: String): SavedGameLoadResult
    suspend fun loadLatestActive(): SavedGameLoadResult
    fun observeLatestActive(): Flow<SavedGameLoadResult>
    suspend fun listSavedGames(): List<GameSession>
    suspend fun delete(gameId: String)
    suspend fun deleteAll()
}

sealed class SaveSessionResult {
    data class Success(val session: GameSession) : SaveSessionResult()
    data class Failure(val reason: String) : SaveSessionResult()
}
