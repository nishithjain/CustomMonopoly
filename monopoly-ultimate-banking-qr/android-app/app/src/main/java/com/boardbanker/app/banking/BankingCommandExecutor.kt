package com.boardbanker.app.banking

import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession
import java.util.concurrent.atomic.AtomicBoolean

sealed class BankingCommitOutcome {
    data class Success(val session: GameSession, val result: GameResult) : BankingCommitOutcome()
    data class DebtRequired(val session: GameSession, val result: GameResult) : BankingCommitOutcome()
    data class Bankruptcy(val session: GameSession, val result: GameResult) : BankingCommitOutcome()
    data class Rejected(val result: GameResult) : BankingCommitOutcome()
    data class PersistenceFailed(val result: GameResult, val reason: String) : BankingCommitOutcome()
}

class BankingCommandExecutor(
    private val sessionManager: ActiveGameSessionManager,
) {
    private val commandLock = AtomicBoolean(false)

    suspend fun execute(command: GameCommand): BankingCommitOutcome? {
        if (!commandLock.compareAndSet(false, true)) return null
        return try {
            val session = sessionManager.currentSession()
                ?: return BankingCommitOutcome.Rejected(
                    GameResult(
                        session = GameSession(gameId = "missing"),
                        outcome = GameOutcome.REJECTED,
                    ),
                )
            when (val commit = sessionManager.processCommand(session, command)) {
                is ProcessCommitResult.Committed -> classify(commit.session, commit.result)
                is ProcessCommitResult.Rejected -> BankingCommitOutcome.Rejected(commit.result)
                is ProcessCommitResult.PersistenceFailed ->
                    BankingCommitOutcome.PersistenceFailed(commit.result, commit.reason)
            }
        } finally {
            commandLock.set(false)
        }
    }

    private fun classify(session: GameSession, result: GameResult): BankingCommitOutcome =
        when (result.outcome) {
            GameOutcome.DEBT_RESOLUTION_REQUIRED -> BankingCommitOutcome.DebtRequired(session, result)
            GameOutcome.BANKRUPTCY -> BankingCommitOutcome.Bankruptcy(session, result)
            else -> BankingCommitOutcome.Success(session, result)
        }
}
