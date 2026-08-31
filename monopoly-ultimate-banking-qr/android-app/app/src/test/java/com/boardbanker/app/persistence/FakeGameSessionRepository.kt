package com.boardbanker.app.persistence

import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.persistence.repository.ObservableRawSavedGameReader
import com.boardbanker.app.persistence.repository.SaveSessionResult
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.GameSessionSchema
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.RawSavedGame
import com.boardbanker.core.persistence.RawSavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGameSessionRepository : GameSessionRepository, ObservableRawSavedGameReader {
    private val games = mutableMapOf<String, GameSession>()
    private val latestFlow = MutableStateFlow<RawSavedGameLoadResult>(RawSavedGameLoadResult.NotFound)
    private val serializer = KotlinGameSessionSerializer()

    var saveCallCount: Int = 0
        private set

    override suspend fun save(session: GameSession): SaveSessionResult {
        saveCallCount++
        games[session.gameId] = session
        latestFlow.value = toRawResult(session)
        return SaveSessionResult.Success(session)
    }

    override suspend fun load(gameId: String): SavedGameLoadResult =
        when (val raw = readRaw(gameId)) {
            is RawSavedGameLoadResult.Success ->
                SavedGameLoadResult.Success(serializer.deserialize(raw.raw.sessionJson))
            is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
            is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(raw.reason)
            is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                found = raw.found,
                supported = raw.supported,
            )
        }

    override suspend fun loadLatestActive(): SavedGameLoadResult =
        when (val raw = readLatestRaw()) {
            is RawSavedGameLoadResult.Success ->
                SavedGameLoadResult.Success(serializer.deserialize(raw.raw.sessionJson))
            is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
            is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(raw.reason)
            is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                found = raw.found,
                supported = raw.supported,
            )
        }

    override fun observeLatestActive(): Flow<SavedGameLoadResult> =
        observeLatestRaw().map { raw ->
            when (raw) {
                is RawSavedGameLoadResult.Success ->
                    SavedGameLoadResult.Success(serializer.deserialize(raw.raw.sessionJson))
                is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
                is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(raw.reason)
                is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                    found = raw.found,
                    supported = raw.supported,
                )
            }
        }

    override suspend fun listSavedGames(): List<GameSession> = games.values.toList()

    override suspend fun delete(gameId: String) {
        games.remove(gameId)
        if (games.isEmpty()) {
            latestFlow.value = RawSavedGameLoadResult.NotFound
        }
    }

    override suspend fun deleteAll() {
        games.clear()
        latestFlow.value = RawSavedGameLoadResult.NotFound
    }

    override suspend fun readRaw(gameId: String): RawSavedGameLoadResult =
        games[gameId]?.let { toRawResult(it) } ?: RawSavedGameLoadResult.NotFound

    override suspend fun readLatestRaw(): RawSavedGameLoadResult =
        games.values.maxByOrNull { it.transactions.size }?.let { toRawResult(it) }
            ?: RawSavedGameLoadResult.NotFound

    override fun observeLatestRaw(): Flow<RawSavedGameLoadResult> = latestFlow

    private fun toRawResult(session: GameSession): RawSavedGameLoadResult.Success =
        RawSavedGameLoadResult.Success(
            RawSavedGame(
                sessionJson = serializer.serialize(session),
                schemaVersion = GameSessionSchema.CURRENT_VERSION,
            ),
        )
}
