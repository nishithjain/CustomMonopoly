package com.boardbanker.app.persistence.repository

import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.RawSavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EditionAwareGameSessionRepository(
    private val rawReader: ObservableRawSavedGameReader,
    private val storage: GameSessionRepository,
    private val restoreOrchestrator: SavedGameRestoreOrchestrator,
) : GameSessionRepository {
    override suspend fun save(session: GameSession): SaveSessionResult {
        when (val validationFailure = restoreOrchestrator.validateForSave(session)) {
            is SavedGameLoadResult.SessionValidationFailed ->
                return SaveSessionResult.Failure(
                    "Cannot save ${validationFailure.editionId} game: ${validationFailure.reason}",
                )
            is SavedGameLoadResult.MissingEdition ->
                return SaveSessionResult.Failure(validationFailure.reason)
            null -> Unit
            else -> Unit
        }
        return storage.save(session)
    }

    override suspend fun load(gameId: String): SavedGameLoadResult =
        restoreFromRaw(rawReader.readRaw(gameId))

    override suspend fun loadLatestActive(): SavedGameLoadResult =
        restoreFromRaw(rawReader.readLatestRaw())

    override fun observeLatestActive(): Flow<SavedGameLoadResult> =
        rawReader.observeLatestRaw().map { restoreFromRaw(it) }

    override suspend fun listSavedGames(): List<GameSession> =
        storage.listSavedGames()

    override suspend fun delete(gameId: String) = storage.delete(gameId)

    override suspend fun deleteAll() = storage.deleteAll()

    private fun restoreFromRaw(rawResult: RawSavedGameLoadResult): SavedGameLoadResult =
        when (rawResult) {
            is RawSavedGameLoadResult.Success -> restoreOrchestrator.restore(rawResult.raw)
            is RawSavedGameLoadResult.NotFound -> SavedGameLoadResult.NotFound
            is RawSavedGameLoadResult.Corrupted -> SavedGameLoadResult.Corrupted(rawResult.reason)
            is RawSavedGameLoadResult.IncompatibleVersion -> SavedGameLoadResult.IncompatibleVersion(
                found = rawResult.found,
                supported = rawResult.supported,
            )
        }
}
