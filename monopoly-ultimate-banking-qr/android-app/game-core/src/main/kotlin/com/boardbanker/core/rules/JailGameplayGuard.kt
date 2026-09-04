package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object JailGameplayGuard {
    fun boardActionBlockedMessage(
        definitions: GameDefinitions,
        session: GameSession,
        playerId: String,
    ): String? {
        if (!definitions.policies.jail.whileInJailCannotResolveBoardActions()) return null
        val player = session.players[playerId] ?: return null
        if (!player.jailStatus) return null
        return blockedMessage(definitions, playerId)
    }

    fun blockedMessage(definitions: GameDefinitions, playerId: String): String {
        val name = definitions.players[playerId]?.displayName ?: playerId
        return "$name is in Jail and must complete the jail-release action before continuing."
    }

    fun propertyPurchaseBlockedMessage(definitions: GameDefinitions, playerId: String): String {
        val name = definitions.players[playerId]?.displayName ?: playerId
        return "$name is in Jail and must get out before purchasing a property."
    }

    fun propertyPurchaseBlockedMessage(
        definitions: GameDefinitions,
        session: GameSession,
        playerId: String,
    ): String? {
        if (!definitions.policies.jail.whileInJailCannotResolveBoardActions()) return null
        val player = session.players[playerId] ?: return null
        if (!player.jailStatus) return null
        return propertyPurchaseBlockedMessage(definitions, playerId)
    }

    fun activePlayerJailGuidance(definitions: GameDefinitions, session: GameSession): String? {
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return null
        if (session.players[activePlayerId]?.jailStatus != true) return null
        val name = definitions.players[activePlayerId]?.displayName ?: activePlayerId
        return "Player $name is in Jail, Get out of Jail before continuing."
    }

    fun clearPendingGameplayForPlayer(session: GameSession, playerId: String): GameSession {
        var updated = session
        if (updated.pendingEventExecution?.actingPlayerId == playerId) {
            updated = updated.copy(pendingEventExecution = null)
        }
        if (updated.pendingEventChoice?.actingPlayerId == playerId) {
            updated = updated.copy(pendingEventChoice = null)
        }
        if (updated.pendingDiceGamble?.actingPlayerId == playerId) {
            updated = updated.copy(pendingDiceGamble = null)
        }
        if (updated.pendingEventDraw?.actingPlayerId == playerId) {
            updated = updated.copy(pendingEventDraw = null)
        }
        if (updated.pendingEnergyGridLanding?.actingPlayerId == playerId) {
            updated = updated.copy(pendingEnergyGridLanding = null)
        }
        return updated
    }
}
