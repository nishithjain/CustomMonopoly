package com.boardbanker.core.rules

import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.model.TurnState
import com.boardbanker.core.transaction.TransactionFactory

class TurnScheduler(
    private val transactionFactory: TransactionFactory,
    private val extraTurnRules: ExtraTurnRules = ExtraTurnRules(transactionFactory),
) {
    fun endTurn(
        session: GameSession,
        endingPlayerId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): TurnTransitionResult {
        if (session.status != GameStatus.ACTIVE) {
            return TurnTransitionResult.failure("Game is not active")
        }
        val turnState = session.turnState
            ?: return TurnTransitionResult.failure("Turn order is not initialized")
        if (endingPlayerId != turnState.activePlayerId) {
            return TurnTransitionResult.failure("Not this player's turn")
        }
        if (session.debtResolution != null) {
            return TurnTransitionResult.failure("Cannot end turn during debt resolution")
        }
        if (session.auction != null) {
            return TurnTransitionResult.failure("Cannot end turn during an auction")
        }
        if (session.pendingEventExecution != null || session.pendingEventChoice != null) {
            return TurnTransitionResult.failure("Cannot end turn while an event is in progress")
        }
        if (session.pendingDiceGamble != null) {
            return TurnTransitionResult.failure("Cannot end turn while Lucky Break is in progress")
        }
        if (session.pendingEventDraw != null) {
            return TurnTransitionResult.failure("Cannot end turn while Lucky Draw is in progress")
        }

        val eligibleOrder = eligibleTurnOrder(session, turnState)
        if (eligibleOrder.isEmpty()) {
            return TurnTransitionResult.failure("No eligible players remain")
        }

        val undoSnapshot = session.snapshot()
        val endingPlayer = session.players[endingPlayerId]
            ?: return TurnTransitionResult.failure("Unknown player")

        if (turnState.turnKind == TurnKind.EXTRA) {
            return advanceToNextPlayer(
                session = session,
                turnState = turnState,
                eligibleOrder = eligibleOrder,
                endingPlayerId = endingPlayerId,
                undoSnapshot = undoSnapshot,
                timestamp = timestamp,
            )
        }

        val canStartExtraTurn = endingPlayer.pendingExtraTurn && !endingPlayer.jailStatus
        if (canStartExtraTurn && endingPlayer.pendingSkipTurnCount > 0) {
            return cancelExtraTurnBySkipAndAdvance(
                session = session,
                turnState = turnState,
                eligibleOrder = eligibleOrder,
                endingPlayer = endingPlayer,
                endingPlayerId = endingPlayerId,
                undoSnapshot = undoSnapshot,
                timestamp = timestamp,
            )
        }

        if (canStartExtraTurn) {
            return startExtraTurn(
                session = session,
                turnState = turnState,
                endingPlayer = endingPlayer,
                endingPlayerId = endingPlayerId,
                undoSnapshot = undoSnapshot,
                timestamp = timestamp,
            )
        }

        return advanceToNextPlayer(
            session = session,
            turnState = turnState,
            eligibleOrder = eligibleOrder,
            endingPlayerId = endingPlayerId,
            undoSnapshot = undoSnapshot,
            timestamp = timestamp,
        )
    }

    private fun startExtraTurn(
        session: GameSession,
        turnState: TurnState,
        endingPlayer: PlayerState,
        endingPlayerId: String,
        undoSnapshot: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
    ): TurnTransitionResult {
        var updatedSession = session.copy(
            players = session.players + (endingPlayerId to endingPlayer.copy(pendingExtraTurn = false)),
        )
        updatedSession = extraTurnRules.beginTurn(updatedSession)
        val (startTx, sessionAfterStart) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EXTRA_TURN_STARTED,
            timestamp = timestamp,
            playerId = endingPlayerId,
            reversible = true,
        )
        updatedSession = sessionAfterStart.copy(
            turnState = turnState.copy(turnKind = TurnKind.EXTRA),
            undoSnapshot = undoSnapshot,
        )
        return TurnTransitionResult.success(
            session = updatedSession,
            transactions = listOf(startTx),
            skippedTurnPlayerIds = emptyList(),
            extraTurnStartedPlayerId = endingPlayerId,
        )
    }

    private fun cancelExtraTurnBySkipAndAdvance(
        session: GameSession,
        turnState: TurnState,
        eligibleOrder: List<String>,
        endingPlayer: PlayerState,
        endingPlayerId: String,
        undoSnapshot: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
    ): TurnTransitionResult {
        var updatedSession = session.copy(
            players = session.players + (
                endingPlayerId to endingPlayer.copy(
                    pendingSkipTurnCount = endingPlayer.pendingSkipTurnCount - 1,
                    pendingExtraTurn = false,
                )
                ),
        )
        val transactions = mutableListOf<Transaction>()
        val (cancelTx, sessionAfterCancel) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP,
            timestamp = timestamp,
            playerId = endingPlayerId,
            reversible = true,
        )
        updatedSession = sessionAfterCancel
        transactions += cancelTx

        return advanceToNextPlayer(
            session = updatedSession,
            turnState = turnState,
            eligibleOrder = eligibleOrder,
            endingPlayerId = endingPlayerId,
            undoSnapshot = undoSnapshot,
            timestamp = timestamp,
            existingTransactions = transactions,
            extraTurnCancelledBySkipPlayerId = endingPlayerId,
        )
    }

    private fun advanceToNextPlayer(
        session: GameSession,
        turnState: TurnState,
        eligibleOrder: List<String>,
        endingPlayerId: String,
        undoSnapshot: com.boardbanker.core.model.SessionSnapshot,
        timestamp: Long,
        existingTransactions: List<Transaction> = emptyList(),
        extraTurnCancelledBySkipPlayerId: String? = null,
    ): TurnTransitionResult {
        var updatedSession = session
        val transactions = existingTransactions.toMutableList()
        val skippedPlayerIds = mutableListOf<String>()

        val scheduling = scheduleNextPlayablePlayer(
            session = updatedSession,
            eligibleOrder = eligibleOrder,
            currentActivePlayerId = endingPlayerId,
            timestamp = timestamp,
        ) ?: return TurnTransitionResult.failure("Unable to schedule the next turn")

        updatedSession = scheduling.session
        transactions += scheduling.transactions
        skippedPlayerIds += scheduling.skippedPlayerIds

        val nextActivePlayerId = scheduling.nextActivePlayerId
        val (advanceTx, sessionAfterAdvance) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.TURN_ADVANCED,
            timestamp = timestamp,
            fromEntity = endingPlayerId,
            toEntity = nextActivePlayerId,
            playerId = nextActivePlayerId,
            reversible = true,
        )
        updatedSession = extraTurnRules.beginTurn(sessionAfterAdvance).copy(
            turnState = turnState.copy(
                activePlayerId = nextActivePlayerId,
                turnKind = TurnKind.NORMAL,
            ),
            undoSnapshot = undoSnapshot,
        )

        return TurnTransitionResult.success(
            session = updatedSession,
            transactions = transactions + advanceTx,
            skippedTurnPlayerIds = skippedPlayerIds,
            extraTurnCancelledBySkipPlayerId = extraTurnCancelledBySkipPlayerId,
        )
    }

    internal fun scheduleNextPlayablePlayer(
        session: GameSession,
        eligibleOrder: List<String>,
        currentActivePlayerId: String,
        timestamp: Long,
    ): SchedulingPassResult? {
        if (eligibleOrder.isEmpty()) return null
        val startIndex = eligibleOrder.indexOf(currentActivePlayerId).let { index ->
            if (index < 0) 0 else (index + 1) % eligibleOrder.size
        }
        val maxIterations = maxOf(eligibleOrder.size * 2, 4).coerceAtMost(MAX_SCHEDULING_ITERATIONS)
        var updatedSession = session
        val transactions = mutableListOf<Transaction>()
        val skippedPlayerIds = mutableListOf<String>()
        var candidateIndex = startIndex
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++
            val candidateId = eligibleOrder[candidateIndex]
            val player = updatedSession.players[candidateId] ?: return null
            if (player.pendingSkipTurnCount > 0) {
                val updatedPlayer = player.copy(pendingSkipTurnCount = player.pendingSkipTurnCount - 1)
                updatedSession = updatedSession.copy(
                    players = updatedSession.players + (candidateId to updatedPlayer),
                )
                val (skipTx, sessionAfterSkip) = transactionFactory.create(
                    session = updatedSession,
                    type = TransactionType.TURN_SKIPPED,
                    timestamp = timestamp,
                    playerId = candidateId,
                    reversible = true,
                )
                updatedSession = sessionAfterSkip
                transactions += skipTx
                skippedPlayerIds += candidateId
                candidateIndex = (candidateIndex + 1) % eligibleOrder.size
                continue
            }
            return SchedulingPassResult(
                session = updatedSession,
                transactions = transactions,
                skippedPlayerIds = skippedPlayerIds,
                nextActivePlayerId = candidateId,
            )
        }
        return null
    }

    companion object {
        const val MAX_SCHEDULING_ITERATIONS = 16

        fun initialTurnState(players: Map<String, PlayerState>): TurnState {
            val order = players.keys.sorted()
            require(order.isNotEmpty()) { "Cannot initialize turn order without players" }
            return TurnState(activePlayerId = order.first(), turnOrder = order, turnKind = TurnKind.NORMAL)
        }

        fun eligibleTurnOrder(session: GameSession, turnState: TurnState): List<String> =
            turnState.turnOrder.filter { playerId ->
                val player = session.players[playerId]
                player != null && player.active && !player.bankrupt
            }
    }

    data class SchedulingPassResult(
        val session: GameSession,
        val transactions: List<Transaction>,
        val skippedPlayerIds: List<String>,
        val nextActivePlayerId: String,
    )

    data class TurnTransitionResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val skippedTurnPlayerIds: List<String>,
        val extraTurnStartedPlayerId: String? = null,
        val extraTurnCancelledBySkipPlayerId: String? = null,
        val error: String?,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<Transaction>,
                skippedTurnPlayerIds: List<String>,
                extraTurnStartedPlayerId: String? = null,
                extraTurnCancelledBySkipPlayerId: String? = null,
            ) = TurnTransitionResult(
                session = session,
                transactions = transactions,
                skippedTurnPlayerIds = skippedTurnPlayerIds,
                extraTurnStartedPlayerId = extraTurnStartedPlayerId,
                extraTurnCancelledBySkipPlayerId = extraTurnCancelledBySkipPlayerId,
                error = null,
            )

            fun failure(message: String) =
                TurnTransitionResult(null, emptyList(), emptyList(), error = message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
