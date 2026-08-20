package com.boardbanker.app.persistence.repository

import com.boardbanker.app.persistence.db.SavedGameDao
import com.boardbanker.app.persistence.mapper.SavedGameMapper
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.GameSessionSchema
import com.boardbanker.core.persistence.GameSessionSerializer
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SessionRestoreValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGameSessionRepository(
    private val dao: SavedGameDao,
    private val serializer: GameSessionSerializer,
    private val restoreValidator: SessionRestoreValidator,
) : GameSessionRepository {
    override suspend fun save(session: GameSession): SaveSessionResult {
        return try {
            val now = System.currentTimeMillis()
            val existing = dao.getGame(session.gameId)
            val sessionJson = serializer.serialize(session)
            val entity = SavedGameMapper.toEntity(
                session = session,
                sessionJson = sessionJson,
                nowMillis = now,
                existing = existing,
            )
            dao.upsertGame(entity)
            SaveSessionResult.Success(session)
        } catch (ex: Exception) {
            SaveSessionResult.Failure(ex.message ?: "Failed to save game session")
        }
    }

    override suspend fun load(gameId: String): SavedGameLoadResult =
        dao.getGame(gameId)?.let { loadFromEntity(it) } ?: SavedGameLoadResult.NotFound

    override suspend fun loadLatestActive(): SavedGameLoadResult =
        dao.getLatestActiveGame()?.let { loadFromEntity(it) } ?: SavedGameLoadResult.NotFound

    override fun observeLatestActive(): Flow<SavedGameLoadResult> =
        dao.observeLatestActiveGame().map { entity ->
            entity?.let { loadFromEntity(it) } ?: SavedGameLoadResult.NotFound
        }

    override suspend fun listSavedGames(): List<GameSession> =
        dao.getAllGames().mapNotNull { entity ->
            (loadFromEntity(entity) as? SavedGameLoadResult.Success)?.session
        }

    override suspend fun delete(gameId: String) {
        dao.deleteGame(gameId)
    }

    override suspend fun deleteAll() {
        dao.deleteAllGames()
    }

    private fun loadFromEntity(entity: com.boardbanker.app.persistence.entity.SavedGameEntity): SavedGameLoadResult {
        if (entity.schemaVersion > GameSessionSchema.CURRENT_VERSION) {
            return SavedGameLoadResult.IncompatibleVersion(
                found = entity.schemaVersion,
                supported = GameSessionSchema.CURRENT_VERSION,
            )
        }

        val session = try {
            serializer.deserialize(entity.sessionJson)
        } catch (ex: Exception) {
            return SavedGameLoadResult.Corrupted(ex.message ?: "Invalid session JSON")
        }

        val validationProblems = restoreValidator.validate(session)
        if (validationProblems.isNotEmpty()) {
            return SavedGameLoadResult.Corrupted(validationProblems.joinToString("; "))
        }

        return SavedGameLoadResult.Success(session)
    }

    companion object {
        fun createValidator(definitions: GameDefinitions): SessionRestoreValidator =
            SessionRestoreValidator(definitions)
    }
}
