package com.boardbanker.app.scanner.ui

import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.EnergyGridDisplayNames
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.app.player.CommonUiIcon

object RecognizedCardSummaryPresentation {
    fun isGameCardSummary(cardType: CardType): Boolean =
        cardType == CardType.PROPERTY || cardType == CardType.EVENT || cardType == CardType.ENERGY_GRID

    fun typeLabel(cardType: CardType): String = when (cardType) {
        CardType.EVENT -> "Event"
        CardType.PROPERTY -> "Property"
        CardType.ENERGY_GRID -> "Energy Grid"
        CardType.USER -> "Player"
    }

    fun typeIcon(cardType: CardType): CommonUiIcon = when (cardType) {
        CardType.EVENT -> CommonUiIcon.EVENT_CARD
        CardType.PROPERTY -> CommonUiIcon.PROPERTY
        CardType.ENERGY_GRID -> CommonUiIcon.ENERGY_GRID
        CardType.USER -> CommonUiIcon.PLAYER_DETAILS
    }

    fun displayName(
        card: ResolvedCard,
        definitions: GameDefinitions?,
    ): String = displayName(
        cardType = card.cardType,
        cardId = card.cardId,
        fallbackDisplayName = card.displayName,
        definitions = definitions,
    )

    fun displayName(
        cardType: CardType,
        cardId: String,
        fallbackDisplayName: String,
        definitions: GameDefinitions?,
    ): String {
        if (definitions == null) {
            return fallbackDisplayName.takeIf { it.isNotBlank() } ?: cardId
        }
        return when (cardType) {
            CardType.PROPERTY -> PropertyDisplayNames.displayNameWithNumber(cardId, definitions)
            CardType.EVENT -> definitions.events[cardId]?.name
                ?.takeIf { it.isNotBlank() }
                ?: fallbackDisplayName.takeIf { it.isNotBlank() }
                ?: cardId
            CardType.ENERGY_GRID -> EnergyGridDisplayNames.displayNameWithNumber(cardId, definitions)
            else -> fallbackDisplayName.takeIf { it.isNotBlank() } ?: cardId
        }
    }

    fun propertyColorGroup(cardId: String, definitions: GameDefinitions?): String? =
        definitions?.properties?.get(cardId)?.colorGroup?.takeIf { it.isNotBlank() }
}
