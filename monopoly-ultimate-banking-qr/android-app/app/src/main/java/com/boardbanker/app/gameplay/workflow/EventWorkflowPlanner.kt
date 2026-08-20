package com.boardbanker.app.gameplay.workflow

import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EventEngineRule

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
    val pattern: EventWorkflowPattern,
    val steps: List<EventScanStep>,
)

object EventWorkflowPlanner {
    fun plan(eventId: String, rule: EventEngineRule): EventWorkflowPlan {
        val pattern = classify(rule)
        val steps = buildSteps(pattern, rule)
        return EventWorkflowPlan(eventId = eventId, pattern = pattern, steps = steps)
    }

    fun classify(rule: EventEngineRule): EventWorkflowPattern = when (rule.actionType) {
        "MOVE_THEN_PROPERTY_CHOICE" -> EventWorkflowPattern.MOVE_THEN_PROPERTY_CHOICE
        "TEMPORARY_RENT_CAP", "TOTAL_GRIDLOCK_V1" -> EventWorkflowPattern.EVENT_ONLY
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

    fun expectedCardType(step: EventScanStep?): CardType? = when (step) {
        EventScanStep.ACTING_PLAYER, EventScanStep.TARGET_PLAYER -> CardType.USER
        EventScanStep.PROPERTY, EventScanStep.SECOND_PROPERTY -> CardType.PROPERTY
        EventScanStep.CONFIRM, null -> null
    }

    fun scanPrompt(step: EventScanStep?): String = when (step) {
        EventScanStep.ACTING_PLAYER ->
            "Scan the Player card of the player who drew this Event."
        EventScanStep.TARGET_PLAYER ->
            "Scan the required Player card."
        EventScanStep.PROPERTY ->
            "Scan the Property card affected by this Event."
        EventScanStep.SECOND_PROPERTY ->
            "Scan the second Property card."
        EventScanStep.CONFIRM -> "Review and confirm this Event."
        null -> "Scan a game card."
    }

    fun buildApplyCommand(
        eventId: String,
        actingPlayerId: String,
        targetPlayerId: String? = null,
        propertyId: String? = null,
        secondPropertyId: String? = null,
    ): GameCommand.ApplyEvent = GameCommand.ApplyEvent(
        eventId = eventId,
        actingPlayerId = actingPlayerId,
        propertyId = propertyId,
        targetPlayerId = targetPlayerId,
        secondPropertyId = secondPropertyId,
    )

    fun coverageForAllEvents(eventIds: List<String>, rules: Map<String, EventEngineRule>): Map<String, EventWorkflowPattern> =
        eventIds.associateWith { eventId ->
            val rule = rules[eventId] ?: error("Missing rule for $eventId")
            classify(rule)
        }
}
