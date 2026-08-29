package com.boardbanker.app.banking

import com.boardbanker.core.card.CardType
import java.util.concurrent.atomic.AtomicBoolean

data class UndoAuthorizationPlayer(
    val playerId: String,
    val displayName: String,
    val verified: Boolean,
)

enum class UndoAuthorizationPhase {
    IDLE,
    COLLECTING,
    COMPLETING,
    COMPLETED,
    FAILED,
}

data class UndoAuthorizationState(
    val phase: UndoAuthorizationPhase = UndoAuthorizationPhase.IDLE,
    val players: List<UndoAuthorizationPlayer> = emptyList(),
    val scanMessage: String? = null,
    val undoDescription: String? = null,
) {
    val active: Boolean
        get() = phase == UndoAuthorizationPhase.COLLECTING ||
            phase == UndoAuthorizationPhase.COMPLETING ||
            phase == UndoAuthorizationPhase.FAILED

    val verifiedCount: Int get() = players.count { it.verified }
    val totalCount: Int get() = players.size
    val waitingPlayers: List<UndoAuthorizationPlayer> get() = players.filter { !it.verified }
    val allVerified: Boolean get() = players.isNotEmpty() && players.all { it.verified }
}

sealed class UndoAuthorizationScanResult {
    data class PlayerVerified(
        val state: UndoAuthorizationState,
        val readyToUndo: Boolean,
    ) : UndoAuthorizationScanResult()

    data class AlreadyApproved(
        val playerName: String,
        val state: UndoAuthorizationState,
    ) : UndoAuthorizationScanResult()

    data class WrongCard(val state: UndoAuthorizationState) : UndoAuthorizationScanResult()

    data class UnregisteredPlayer(val state: UndoAuthorizationState) : UndoAuthorizationScanResult()

    data class Ignored(val state: UndoAuthorizationState) : UndoAuthorizationScanResult()
}

/**
 * Transient multi-player approval for Undo Last Action.
 *
 * Not persisted. Does not mutate [com.boardbanker.core.model.GameSession]
 * or consume the undo snapshot.
 */
class UndoAuthorizationController {
    private val lock = Any()
    private val completing = AtomicBoolean(false)
    private var state = UndoAuthorizationState()

    fun snapshot(): UndoAuthorizationState = synchronized(lock) { state }

    fun begin(
        players: List<UndoAuthorizationPlayer>,
        undoDescription: String?,
    ): UndoAuthorizationState = synchronized(lock) {
        completing.set(false)
        state = UndoAuthorizationState(
            phase = UndoAuthorizationPhase.COLLECTING,
            players = players.map { it.copy(verified = false) },
            scanMessage = null,
            undoDescription = undoDescription,
        )
        state
    }

    fun cancel(): UndoAuthorizationState = synchronized(lock) {
        completing.set(false)
        state = UndoAuthorizationState()
        state
    }

    fun onScan(cardType: CardType, cardId: String): UndoAuthorizationScanResult = synchronized(lock) {
        if (state.phase != UndoAuthorizationPhase.COLLECTING) {
            return UndoAuthorizationScanResult.Ignored(state)
        }
        if (cardType != CardType.USER) {
            state = state.copy(scanMessage = WRONG_CARD_MESSAGE)
            return UndoAuthorizationScanResult.WrongCard(state)
        }
        val player = state.players.firstOrNull { it.playerId == cardId }
        if (player == null) {
            state = state.copy(scanMessage = UNREGISTERED_PLAYER_MESSAGE)
            return UndoAuthorizationScanResult.UnregisteredPlayer(state)
        }
        if (player.verified) {
            state = state.copy(scanMessage = alreadyApprovedMessage(player.displayName))
            return UndoAuthorizationScanResult.AlreadyApproved(player.displayName, state)
        }

        val updatedPlayers = state.players.map {
            if (it.playerId == cardId) it.copy(verified = true) else it
        }
        val allVerified = updatedPlayers.isNotEmpty() && updatedPlayers.all { it.verified }
        if (allVerified) {
            if (!completing.compareAndSet(false, true)) {
                return UndoAuthorizationScanResult.Ignored(state)
            }
            state = state.copy(
                phase = UndoAuthorizationPhase.COMPLETING,
                players = updatedPlayers,
                scanMessage = null,
            )
            return UndoAuthorizationScanResult.PlayerVerified(state, readyToUndo = true)
        }
        state = state.copy(
            players = updatedPlayers,
            scanMessage = null,
        )
        UndoAuthorizationScanResult.PlayerVerified(state, readyToUndo = false)
    }

    fun markFailed(message: String): UndoAuthorizationState = synchronized(lock) {
        // Stay locked so scanner callbacks cannot start a second undo.
        completing.set(true)
        state = state.copy(
            phase = UndoAuthorizationPhase.FAILED,
            scanMessage = message,
        )
        state
    }

    fun markCompleted(): UndoAuthorizationState = synchronized(lock) {
        completing.set(true)
        state = UndoAuthorizationState(phase = UndoAuthorizationPhase.COMPLETED)
        state
    }

    companion object {
        const val WRONG_CARD_MESSAGE = "Please scan a Player Card to approve the undo."
        const val UNREGISTERED_PLAYER_MESSAGE = "This Player Card does not belong to the current game."
        const val NOTHING_TO_UNDO_MESSAGE = "There is no action available to undo."
        const val SUCCESS_MESSAGE = "Last action undone successfully."
        const val ALL_PLAYERS_MUST_APPROVE_TITLE = "All players must approve the undo"
        const val ALL_PLAYERS_MUST_APPROVE_BODY =
            "To undo the last action, every player must scan their Player Card.\n" +
                "The action will be undone after all players have scanned."

        fun alreadyApprovedMessage(playerName: String): String =
            "$playerName has already approved the undo."

        fun progressLabel(verified: Int, total: Int): String =
            "Players verified: $verified of $total"
    }
}
