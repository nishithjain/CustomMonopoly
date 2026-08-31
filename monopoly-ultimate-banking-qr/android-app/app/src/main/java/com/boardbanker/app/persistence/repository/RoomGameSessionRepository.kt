package com.boardbanker.app.persistence.repository

import com.boardbanker.app.persistence.db.SavedGameDao
import com.boardbanker.app.persistence.mapper.SavedGameMapper
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.GameSessionSchema
import com.boardbanker.core.persistence.GameSessionSerializer
import com.boardbanker.core.persistence.RawSavedGame
import com.boardbanker.core.persistence.RawSavedGameLoadResult
import com.boardbanker.core.persistence.RawSavedGameReader
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGameSessionRepository(
    private val dao: SavedGameDao,
    private val serializer: GameSessionSerializer,
) : GameSessionRepository, ObservableRawSavedGameReader {
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
        readRaw(gameId).toStructuralLoadResult()

    override suspend fun loadLatestActive(): SavedGameLoadResult =
        readLatestRaw().toStructuralLoadResult()

    override fun observeLatestActive(): Flow<SavedGameLoadResult> =
        dao.observeLatestActiveGame().map { entity ->
            entity?.let { readRawFromEntity(it).toStructuralLoadResult() }
                ?: SavedGameLoadResult.NotFound
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

    override suspend fun readRaw(gameId: String): RawSavedGameLoadResult =
        dao.getGame(gameId)?.let { readRawFromEntity(it) } ?: RawSavedGameLoadResult.NotFound

    override suspend fun readLatestRaw(): RawSavedGameLoadResult =
        dao.getLatestActiveGame()?.let { readRawFromEntity(it) } ?: RawSavedGameLoadResult.NotFound

    override fun observeLatestRaw(): Flow<RawSavedGameLoadResult> =
        dao.observeLatestActiveGame().map { entity ->
            entity?.let { readRawFromEntity(it) } ?: RawSavedGameLoadResult.NotFound
        }

    private fun readRawFromEntity(entity: com.boardbanker.app.persistence.entity.SavedGameEntity): RawSavedGameLoadResult {
        if (entity.schemaVersion > GameSessionSchema.CURRENT_VERSION) {
            return RawSavedGameLoadResult.IncompatibleVersion(
                found = entity.schemaVersion,
                supported = GameSessionSchema.CURRENT_VERSION,
            )
        }
        return RawSavedGameLoadResult.Success(
            RawSavedGame(
                sessionJson = entity.sessionJson,
                schemaVersion = entity.schemaVersion,
            ),
        )
    }

    private fun loadFromEntity(entity: com.boardbanker.app.persistence.entity.SavedGameEntity): SavedGameLoadResult =
        when (val raw = readRawFromEntity(entity)) {
            is RawSavedGameLoadResult.Success -> deserializeWithoutValidation(raw.raw.sessionJson)
            is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
            is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(raw.reason)
            is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                found = raw.found,
                supported = raw.supported,
            )
        }

    private fun RawSavedGameLoadResult.toStructuralLoadResult(): SavedGameLoadResult =
        when (this) {
            is RawSavedGameLoadResult.Success -> deserializeWithoutValidation(raw.sessionJson)
            is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
            is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(reason)
            is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                found = found,
                supported = supported,
            )
        }

    private fun deserializeWithoutValidation(sessionJson: String): SavedGameLoadResult =
        try {
            SavedGameLoadResult.Success(serializer.deserialize(sessionJson))
        } catch (ex: Exception) {
            SavedGameLoadResult.Corrupted(ex.message ?: "Invalid session JSON")
        }
}
