package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object EventDrawUiMapper {
    const val INSTRUCTION = "Draw one additional Event Card and scan its QR code."

    fun map(
        session: GameSession,
        definitions: GameDefinitions,
        commandInFlight: Boolean,
    ): EventDrawUiState? {
        val pending = session.pendingEventDraw ?: return null
        val parentEvent = definitions.events[pending.parentEventId] ?: return null
        val chainProgressText = if (pending.maximumChainDepth > 1) {
            "Additional draw ${pending.chainDepth} of ${pending.maximumChainDepth}"
        } else {
            null
        }
        return EventDrawUiState(
            parentEventId = pending.parentEventId,
            parentEventName = parentEvent.name,
            actingPlayerId = pending.actingPlayerId,
            actingPlayerName = PlayerDisplayNames.displayName(session, pending.actingPlayerId, definitions),
            instruction = INSTRUCTION,
            chainProgressText = chainProgressText,
            scanEnabled = !commandInFlight && session.debtResolution == null,
        )
    }
}
