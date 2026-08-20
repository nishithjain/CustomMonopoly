package com.boardbanker.app.player

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object PlayerDisplayNames {
    fun displayName(
        session: GameSession?,
        playerId: String,
        definitions: GameDefinitions,
    ): String {
        val customName = session?.players?.get(playerId)?.playerName?.takeIf { it.isNotBlank() }
        if (customName != null) {
            return customName
        }
        return tokenName(playerId, definitions)
    }

    fun tokenName(playerId: String, definitions: GameDefinitions): String =
        definitions.players[playerId]?.displayName ?: playerId
}
