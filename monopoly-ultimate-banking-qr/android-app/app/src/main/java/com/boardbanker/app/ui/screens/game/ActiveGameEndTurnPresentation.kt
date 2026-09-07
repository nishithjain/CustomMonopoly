package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState

object ActiveGameEndTurnPresentation {
    fun subtitle(activePlayerName: String?): String? =
        activePlayerName?.let { name -> "End ${possessive(name)} turn" }

    fun contentDescription(activePlayerName: String?): String =
        "End ${activePlayerName ?: "active player"}'s turn"

    fun disabledReason(
        activePlayerName: String?,
        actionAvailability: ActiveGameActionAvailability,
        workflowState: GameplayWorkflowState,
        hasPendingDiceGamble: Boolean,
        hasPendingEventDraw: Boolean,
        commandInFlight: Boolean,
        gameplayLocked: Boolean,
        luckyDrawEventName: String = "Lucky Draw",
        luckyBreakEventName: String = "Lucky Break",
    ): String? {
        if (actionAvailability.endTurnEnabled || gameplayLocked) return null
        val playerLabel = possessive(activePlayerName ?: "the active player")

        return when {
            commandInFlight -> "Please wait for the current action to finish."
            hasPendingEventDraw || workflowState is GameplayWorkflowState.EventDrawScanRequired ->
                "Complete $luckyDrawEventName before ending $playerLabel turn."
            hasPendingDiceGamble || workflowState is GameplayWorkflowState.EventDiceGamble ->
                "Complete $luckyBreakEventName before ending $playerLabel turn."
            workflowState is GameplayWorkflowState.EventCollectingTargets ||
                workflowState is GameplayWorkflowState.EventConfirm ||
                workflowState is GameplayWorkflowState.EventPropertyChoice ->
                "Complete the pending Event before ending $playerLabel turn."
            workflowState !is GameplayWorkflowState.Ready ->
                "Finish the current action before ending $playerLabel turn."
            else -> null
        }
    }

    private fun possessive(name: String): String =
        if (name.endsWith('s')) "$name'" else "$name's"
}
