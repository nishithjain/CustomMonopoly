package com.boardbanker.core.persistence

data class RawSavedGame(
    val sessionJson: String,
    val schemaVersion: Int,
)

sealed class RawSavedGameLoadResult {
    data class Success(val raw: RawSavedGame) : RawSavedGameLoadResult()
    data object NotFound : RawSavedGameLoadResult()
    data class Corrupted(val reason: String) : RawSavedGameLoadResult()
    data class IncompatibleVersion(val found: Int, val supported: Int) : RawSavedGameLoadResult()
}

interface RawSavedGameReader {
    suspend fun readRaw(gameId: String): RawSavedGameLoadResult
    suspend fun readLatestRaw(): RawSavedGameLoadResult
}
