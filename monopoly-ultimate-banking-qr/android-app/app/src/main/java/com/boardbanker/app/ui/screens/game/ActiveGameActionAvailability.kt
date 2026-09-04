package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState

/**
 * Central policy for Active Game controls based on the authoritative active player state.
 */
data class ActiveGameActionAvailability(
    val scanCardEnabled: Boolean,
    val endTurnEnabled: Boolean,
    val bankActionsEnabled: Boolean,
    val getOutOfJailEnabled: Boolean,
) {
    companion object {
        fun forActivePlayer(
            activePlayerInJail: Boolean,
            commandInFlight: Boolean,
            gameplayLocked: Boolean,
            workflowState: GameplayWorkflowState,
            hasMandatoryEventPending: Boolean,
            hasPendingDiceGamble: Boolean,
            hasPendingEventDraw: Boolean,
        ): ActiveGameActionAvailability {
            if (gameplayLocked || commandInFlight) {
                return disabled()
            }
            if (hasMandatoryEventPending || hasPendingDiceGamble || hasPendingEventDraw) {
                return disabled()
            }
            if (activePlayerInJail) {
                val readyForTurn = workflowState is GameplayWorkflowState.Ready
                return ActiveGameActionAvailability(
                    scanCardEnabled = false,
                    endTurnEnabled = readyForTurn,
                    bankActionsEnabled = readyForTurn,
                    getOutOfJailEnabled = readyForTurn,
                )
            }
            val readyForTurn = workflowState is GameplayWorkflowState.Ready
            return ActiveGameActionAvailability(
                scanCardEnabled = readyForTurn,
                endTurnEnabled = readyForTurn,
                bankActionsEnabled = readyForTurn,
                getOutOfJailEnabled = false,
            )
        }

        private fun disabled() = ActiveGameActionAvailability(
            scanCardEnabled = false,
            endTurnEnabled = false,
            bankActionsEnabled = false,
            getOutOfJailEnabled = false,
        )
    }
}
