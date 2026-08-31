package com.boardbanker.core.persistence

import com.boardbanker.core.model.GameSession

sealed class SavedGameLoadResult {
    data class Success(val session: GameSession) : SavedGameLoadResult()
    data object NotFound : SavedGameLoadResult()
    data class Corrupted(val reason: String) : SavedGameLoadResult()
    data class IncompatibleVersion(val found: Int, val supported: Int) : SavedGameLoadResult()
    data class IncompatibleEditionVersion(
        val editionId: String,
        val editionName: String,
        val savedVersion: Int,
        val installedVersion: Int,
        val gameStatus: com.boardbanker.core.model.GameStatus? = null,
    ) : SavedGameLoadResult() {
        fun userMessage(): String =
            "Saved game cannot be resumed\n\n" +
                "This game uses $editionName data version $savedVersion, " +
                "but version $installedVersion is currently installed. " +
                "Resuming it could change game values or rules.\n\n" +
                "The saved game has not been changed."
    }

    data class MissingEdition(
        val editionId: String,
        val reason: String,
    ) : SavedGameLoadResult()

    data class SessionValidationFailed(
        val editionId: String,
        val reason: String,
    ) : SavedGameLoadResult()
}
