package com.boardbanker.core.engine

import com.boardbanker.core.error.GameError
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction

data class GameResult(
    val session: GameSession,
    val outcome: GameOutcome = GameOutcome.SUCCESS,
    val transactions: List<Transaction> = emptyList(),
    val physicalActions: List<PhysicalAction> = emptyList(),
    val pendingMessage: String? = null,
    val error: GameError? = null,
    val skippedTurnPlayerIds: List<String> = emptyList(),
    val extraTurnStartedPlayerId: String? = null,
    val extraTurnCancelledBySkipPlayerId: String? = null,
    val extraTurnCancelledByJailPlayerIds: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = error == null && outcome != GameOutcome.REJECTED
}
