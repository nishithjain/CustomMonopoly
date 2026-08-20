package com.boardbanker.core.persistence

import com.boardbanker.core.model.GameSession

sealed class SavedGameLoadResult {
    data class Success(val session: GameSession) : SavedGameLoadResult()
    data object NotFound : SavedGameLoadResult()
    data class Corrupted(val reason: String) : SavedGameLoadResult()
    data class IncompatibleVersion(val found: Int, val supported: Int) : SavedGameLoadResult()
}
