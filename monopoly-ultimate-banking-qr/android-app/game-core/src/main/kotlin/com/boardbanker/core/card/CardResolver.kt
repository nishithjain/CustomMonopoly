package com.boardbanker.core.card

import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.displayNameWithNumber

interface CardResolver {
    fun resolve(qrPayload: String): CardResolution
}

class DefaultCardResolver(
    private val definitions: GameDefinitions,
) : CardResolver {
    override fun resolve(qrPayload: String): CardResolution {
        val normalized = qrPayload.trim()
        if (normalized.isEmpty()) {
            return CardResolution.UnknownQr(qrPayload)
        }
        val card = definitions.cardsByQrPayload[normalized]
            ?: return CardResolution.UnknownQr(qrPayload)
        val displayName = when (card.cardType) {
            CardType.PROPERTY -> definitions.properties[card.cardId]?.displayNameWithNumber() ?: card.name
            CardType.ENERGY_GRID -> definitions.energyGrids[card.cardId]?.name ?: card.name
            else -> card.name
        }
        return CardResolution.Success(
            cardId = card.cardId,
            cardType = card.cardType,
            displayName = displayName,
            qrPayload = card.qrPayload,
        )
    }
}
