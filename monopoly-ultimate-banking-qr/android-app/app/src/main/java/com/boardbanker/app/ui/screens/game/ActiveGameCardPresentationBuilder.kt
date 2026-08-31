package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.displayNameWithNumber

object ActiveGameCardPresentationBuilder {
    fun build(
        state: GameplayWorkflowState,
        definitions: GameDefinitions,
        session: GameSession?,
    ): CardPresentationUi? = when (state) {
        is GameplayWorkflowState.PropertySummary -> {
            val ownerPlayerId = session?.properties?.get(state.propertyId)?.ownerPlayerId
            CardPresentationUi(
                cardTypeLabel = "PROPERTY",
                title = state.propertyName,
                body = if (state.isUnowned) {
                    "Purchase Price:\n${formatMoney(state.purchasePrice ?: 0, definitions)}\n\nStatus:\nUNOWNED"
                } else {
                    buildString {
                        append("Current Rent Level: ${state.rentLevel}\n\n")
                        append("Current Rent: ${formatMoney(state.currentRent ?: 0, definitions)}")
                    }
                },
                buyAmount = state.purchasePrice,
                ownerPlayerId = ownerPlayerId,
                ownerName = state.ownerName,
            )
        }
        is GameplayWorkflowState.UnownedPropertyDecision -> {
            val property = definitions.properties[state.propertyId] ?: return null
            CardPresentationUi(
                cardTypeLabel = "PROPERTY",
                title = property.displayNameWithNumber(),
                body = "Purchase Price:\n${formatMoney(property.purchasePrice, definitions)}\n\nStatus:\nUNOWNED",
                buyAmount = property.purchasePrice,
            )
        }
        is GameplayWorkflowState.WaitingForRentPayer -> {
            val property = definitions.properties[state.propertyId] ?: return null
            val propertyState = session?.properties?.get(state.propertyId)
            val rentLevel = propertyState?.currentRentLevel ?: property.initialRentLevel
            val currentRent = property.rentLevels.firstOrNull { it.level == rentLevel }?.amount
            CardPresentationUi(
                cardTypeLabel = "PROPERTY",
                title = property.displayNameWithNumber(),
                body = buildString {
                    append("Current Rent Level: $rentLevel\n\n")
                    append("Current Rent: ${formatMoney(currentRent ?: 0, definitions)}\n\n")
                    append("Scan the Player who landed here.")
                },
                ownerPlayerId = state.ownerPlayerId,
                ownerName = state.ownerName,
            )
        }
        is GameplayWorkflowState.EventIntro -> CardPresentationUi(
            cardTypeLabel = "EVENT",
            title = state.eventName,
            body = formatEventBody(state.eventSubtitle, state.eventDescription),
        )
        is GameplayWorkflowState.EventCollectingTargets -> {
            val event = definitions.events[state.eventId] ?: return null
            val action = event.actions.getOrNull(state.actionIndex)
            val step = state.plan.steps.getOrNull(state.stepIndex)
            val header = com.boardbanker.app.gameplay.workflow.EventWorkflowPlanner.scanHeaderForPlan(state.plan, step)
            val overlay = com.boardbanker.app.gameplay.workflow.EventWorkflowPlanner.scanPrompt(step)
            CardPresentationUi(
                cardTypeLabel = header.uppercase(),
                title = event.name,
                body = buildString {
                    append("Action ${state.actionIndex + 1} of ${event.actions.size}")
                    if (action != null) {
                        append(": ")
                        append(action.actionType.replace('_', ' '))
                    }
                    append("\n\n")
                    append(overlay)
                },
            )
        }
        is GameplayWorkflowState.EventConfirm -> {
            val event = definitions.events[state.eventId] ?: return null
            CardPresentationUi(
                cardTypeLabel = "EVENT",
                title = event.name,
                body = event.displayText(),
            )
        }
        else -> null
    }

    private fun formatEventBody(subtitle: String, description: String): String = buildString {
        if (subtitle.isNotBlank()) {
            append(subtitle)
            if (description.isNotBlank()) {
                append("\n\n")
            }
        }
        append(description)
    }
}
