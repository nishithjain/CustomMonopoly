package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TemporaryEffect

object PlayerActiveEventEffects {
    fun activeEventNames(
        playerId: String,
        session: GameSession,
        definitions: GameDefinitions,
    ): List<String> {
        val player = session.players[playerId] ?: return emptyList()
        val names = linkedSetOf<String>()

        if (player.pendingRentWaiver) {
            player.rentWaiverSourceEventId?.let { eventId ->
                eventName(definitions, eventId)?.let { names += it }
            }
        }
        if (player.jailPassCount > 0) {
            eventNamesForActionType(definitions, "GET_OUT_OF_JAIL_PASS").forEach { names += it }
        }
        if (player.pendingSkipTurnCount > 0) {
            eventNamesForActionType(definitions, "SKIP_NEXT_TURN").forEach { names += it }
        }
        if (player.pendingExtraTurn) {
            eventNamesForActionType(definitions, "EXTRA_TURN").forEach { names += it }
        }

        session.temporaryEffects
            .asSequence()
            .filter { it.active && it.remainingUses > 0 }
            .filter { effect -> effectAppliesToPlayer(effect, playerId, session) }
            .mapNotNull { effect -> eventName(definitions, effect.createdByEventId) }
            .forEach { names += it }

        return names.toList()
    }

    private fun effectAppliesToPlayer(
        effect: TemporaryEffect,
        playerId: String,
        session: GameSession,
    ): Boolean = when (effect.targetScope) {
        "GLOBAL" -> true
        else -> session.properties[effect.targetScope]?.ownerPlayerId == playerId
    }

    fun eventName(definitions: GameDefinitions, eventId: String): String? =
        definitions.events[eventId]?.name

    fun eventNamesForActionType(definitions: GameDefinitions, actionType: String): List<String> =
        definitions.events.values
            .filter { event -> event.actions.any { it.actionType == actionType } }
            .map { it.name }
            .distinct()
}
