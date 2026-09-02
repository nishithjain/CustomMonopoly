package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventEngineRule
import com.boardbanker.core.model.PendingEventExecution

enum class EventWorkflowPattern {
    EVENT_ONLY,
    ACTING_PLAYER_ONLY,
    PLAYER_TARGET,
    TWO_PLAYER_TARGET,
    PROPERTY_TARGET,
    TWO_PLAYER_TWO_PROPERTY,
    MOVE_THEN_PROPERTY_CHOICE,
}

enum class EventScanStep {
    ACTING_PLAYER,
    TARGET_PLAYER,
    PROPERTY,
    SECOND_PROPERTY,
    CONFIRM,
}

data class EventWorkflowPlan(
    val eventId: String,
    val actionIndex: Int,
    val pattern: EventWorkflowPattern,
    val steps: List<EventScanStep>,
)

object EventWorkflowPlanner {
    fun planForEventAtAction(event: EventDefinition, actionIndex: Int): EventWorkflowPlan {
        require(actionIndex in event.actions.indices) {
            "Event ${event.eventId} has no action at index $actionIndex"
        }
        return planForAction(event.eventId, event.actions[actionIndex], actionIndex)
    }

    fun planForAction(eventId: String, action: EventActionDefinition, actionIndex: Int): EventWorkflowPlan {
        val pattern = classify(action)
        val steps = buildSteps(pattern, action)
        return EventWorkflowPlan(
            eventId = eventId,
            actionIndex = actionIndex,
            pattern = pattern,
            steps = steps,
        )
    }

    /** Legacy single-action helper retained for compatibility tests. */
    fun plan(eventId: String, rule: EventEngineRule): EventWorkflowPlan =
        planForAction(eventId, rule, actionIndex = 0)

    fun classify(rule: EventEngineRule): EventWorkflowPattern = when (rule.actionType) {
        "MOVE_THEN_PROPERTY_CHOICE" -> EventWorkflowPattern.MOVE_THEN_PROPERTY_CHOICE
        "TEMPORARY_RENT_CAP",
        "TOTAL_GRIDLOCK_V1",
        "MOVE_TO_SPACE",
        "MOVE_BACKWARD",
        "MOVE_TO_JAIL",
        "MOVE_TO_NEAREST_STATION",
        "BANK_CREDIT",
        "BANK_DEBIT",
        "PAY_EACH_PLAYER",
        "COLLECT_FROM_EACH_PLAYER",
        "DEBIT_PER_OWNED_PROPERTY",
        "CREDIT_PER_OWNED_PROPERTY",
        "NEXT_RENT_WAIVER",
        "GET_OUT_OF_JAIL_PASS",
        "SKIP_NEXT_TURN",
        "EXTRA_TURN",
        "TOP_UP_BALANCE_TO_THRESHOLD",
        "COMPLETE_COLOR_SET_BONUS_CREDIT",
        "DRAW_ANOTHER_EVENT",
        "GAMBLE_ON_DICE_ROLL",
        -> EventWorkflowPattern.EVENT_ONLY
        "COOPERATIVE_PROPERTY_UPGRADE" -> EventWorkflowPattern.TWO_PLAYER_TWO_PROPERTY
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "FORCED_PROPERTY_SELLBACK",
        -> EventWorkflowPattern.PROPERTY_TARGET
        else -> when (rule.targetType) {
            "CURRENT_PLAYER" -> EventWorkflowPattern.ACTING_PLAYER_ONLY
            "OTHER_PLAYER" -> EventWorkflowPattern.PLAYER_TARGET
            "TWO_PLAYERS" -> EventWorkflowPattern.TWO_PLAYER_TARGET
            "TWO_PLAYERS_AND_PROPERTIES" -> EventWorkflowPattern.TWO_PLAYER_TWO_PROPERTY
            else -> EventWorkflowPattern.PROPERTY_TARGET
        }
    }

    private fun buildSteps(pattern: EventWorkflowPattern, rule: EventEngineRule): List<EventScanStep> {
        val steps = mutableListOf<EventScanStep>(EventScanStep.ACTING_PLAYER)
        when (pattern) {
            EventWorkflowPattern.EVENT_ONLY,
            EventWorkflowPattern.ACTING_PLAYER_ONLY,
            -> Unit
            EventWorkflowPattern.PLAYER_TARGET,
            EventWorkflowPattern.TWO_PLAYER_TARGET,
            -> steps += EventScanStep.TARGET_PLAYER
            EventWorkflowPattern.PROPERTY_TARGET,
            EventWorkflowPattern.MOVE_THEN_PROPERTY_CHOICE,
            -> if (rule.requiresPropertyScan) steps += EventScanStep.PROPERTY
            EventWorkflowPattern.TWO_PLAYER_TWO_PROPERTY -> {
                steps += EventScanStep.TARGET_PLAYER
                steps += EventScanStep.PROPERTY
                steps += EventScanStep.SECOND_PROPERTY
            }
        }
        if (pattern == EventWorkflowPattern.TWO_PLAYER_TWO_PROPERTY ||
            rule.actionType in setOf("SWAP_PROPERTIES", "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS")
        ) {
            steps += EventScanStep.CONFIRM
        }
        return steps
    }

    fun initialStepIndex(
        plan: EventWorkflowPlan,
        actingPlayerId: String?,
        targetPlayerId: String?,
        propertyId: String?,
        secondPropertyId: String?,
    ): Int {
        var index = 0
        while (index < plan.steps.size) {
            when (plan.steps[index]) {
                EventScanStep.ACTING_PLAYER -> if (actingPlayerId != null) index++ else return index
                EventScanStep.TARGET_PLAYER -> if (targetPlayerId != null) index++ else return index
                EventScanStep.PROPERTY -> if (propertyId != null) index++ else return index
                EventScanStep.SECOND_PROPERTY -> if (secondPropertyId != null) index++ else return index
                EventScanStep.CONFIRM -> return index
            }
        }
        return index
    }

    fun planFromPendingExecution(event: EventDefinition, pending: PendingEventExecution): EventWorkflowPlan =
        planForEventAtAction(event, pending.currentActionIndex)

    fun expectedCardType(step: EventScanStep?): CardType? = scanRequest(step).singleExpectedType

    fun scanRequest(step: EventScanStep?): ScanRequest = when (step) {
        EventScanStep.ACTING_PLAYER, EventScanStep.TARGET_PLAYER -> ScanRequest.player()
        EventScanStep.PROPERTY, EventScanStep.SECOND_PROPERTY -> ScanRequest.property()
        EventScanStep.CONFIRM, null -> ScanRequest.gameCard()
    }

    fun scanPrompt(step: EventScanStep?): String = scanRequest(step).instruction

    fun scanHeaderForPlan(plan: EventWorkflowPlan, step: EventScanStep?): String = when (step) {
        EventScanStep.ACTING_PLAYER -> "Scan Player Card"
        EventScanStep.TARGET_PLAYER -> "Scan Player Card"
        EventScanStep.PROPERTY, EventScanStep.SECOND_PROPERTY -> "Scan Property Card"
        EventScanStep.CONFIRM -> "Scan Event Card"
        null -> "Event Action ${plan.actionIndex + 1}"
    }

    fun buildApplyCommand(
        eventId: String,
        actingPlayerId: String,
        targetPlayerId: String? = null,
        propertyId: String? = null,
        secondPropertyId: String? = null,
        secondPlayerId: String? = null,
    ): GameCommand.ApplyEvent = GameCommand.ApplyEvent(
        eventId = eventId,
        actingPlayerId = actingPlayerId,
        propertyId = propertyId,
        targetPlayerId = targetPlayerId,
        secondPropertyId = secondPropertyId,
        secondPlayerId = secondPlayerId,
    )

    fun coverageForAllEvents(eventIds: List<String>, rules: Map<String, EventEngineRule>): Map<String, EventWorkflowPattern> =
        eventIds.associateWith { eventId ->
            val rule = rules[eventId] ?: error("Missing rule for $eventId")
            classify(rule)
        }
}
