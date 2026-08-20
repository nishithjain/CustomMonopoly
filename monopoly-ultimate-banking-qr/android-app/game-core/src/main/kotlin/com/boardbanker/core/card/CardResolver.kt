package com.boardbanker.core.card

import com.boardbanker.core.model.GameDefinitions

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
        return CardResolution.Success(
            cardId = card.cardId,
            cardType = card.cardType,
            displayName = card.name,
            qrPayload = card.qrPayload,
        )
    }
}
