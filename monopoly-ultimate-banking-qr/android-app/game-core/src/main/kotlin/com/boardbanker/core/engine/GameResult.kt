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
) {
    val isSuccess: Boolean get() = error == null && outcome != GameOutcome.REJECTED
}
