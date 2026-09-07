package com.boardbanker.app.gameplay.presentation

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object EventDrawUiMapper {
    const val INSTRUCTION = "Draw one additional Event Card and scan its QR code."
    const val REQUIRED_DRAWS_TEXT = "1 additional Event Card required"
    const val SCAN_BUTTON_LABEL = "Scan Additional Event Card"
    const val OPENING_SCANNER_LABEL = "Opening scanner…"

    fun canScanAdditionalEvent(
        session: GameSession,
        commandInFlight: Boolean,
        scannerLaunchInProgress: Boolean,
    ): Boolean {
        val pending = session.pendingEventDraw ?: return false
        val activePlayerId = session.turnState?.activePlayerId ?: return false
        return pending.remainingDraws == 1 &&
            pending.actingPlayerId == activePlayerId &&
            !commandInFlight &&
            !scannerLaunchInProgress
    }

    fun map(
        session: GameSession,
        definitions: GameDefinitions,
        commandInFlight: Boolean,
        scannerLaunchInProgress: Boolean = false,
    ): EventDrawUiState? {
        val pending = session.pendingEventDraw ?: return null
        val parentEvent = definitions.events[pending.parentEventId] ?: return null
        val scanEnabled = canScanAdditionalEvent(session, commandInFlight, scannerLaunchInProgress)
        return EventDrawUiState(
            parentEventId = pending.parentEventId,
            parentEventName = parentEvent.name,
            actingPlayerId = pending.actingPlayerId,
            actingPlayerName = PlayerDisplayNames.displayName(session, pending.actingPlayerId, definitions),
            instruction = INSTRUCTION,
            requiredDrawsText = REQUIRED_DRAWS_TEXT,
            scanButtonLabel = if (scannerLaunchInProgress) {
                OPENING_SCANNER_LABEL
            } else {
                SCAN_BUTTON_LABEL
            },
            scanEnabled = scanEnabled,
        )
    }
}
