package com.boardbanker.app.game

import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession

sealed class ProcessCommitResult {
    data class Committed(
        val session: GameSession,
        val result: GameResult,
    ) : ProcessCommitResult()

    data class Rejected(
        val result: GameResult,
    ) : ProcessCommitResult()

    data class PersistenceFailed(
        val result: GameResult,
        val reason: String,
    ) : ProcessCommitResult()
}
