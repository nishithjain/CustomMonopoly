package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

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
                    "Purchase Price:\n${formatMoney(state.purchasePrice ?: 0)}\n\nStatus:\nUNOWNED"
                } else {
                    buildString {
                        append("Current Rent Level: ${state.rentLevel}\n\n")
                        append("Current Rent: ${formatMoney(state.currentRent ?: 0)}")
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
                title = property.name,
                body = "Purchase Price:\n${formatMoney(property.purchasePrice)}\n\nStatus:\nUNOWNED",
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
                title = property.name,
                body = buildString {
                    append("Current Rent Level: $rentLevel\n\n")
                    append("Current Rent: ${formatMoney(currentRent ?: 0)}\n\n")
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
            CardPresentationUi(
                cardTypeLabel = "EVENT",
                title = event.name,
                body = event.displayText(),
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
