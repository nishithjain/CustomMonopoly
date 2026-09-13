package com.boardbanker.core.model

object PlayerOrder {
    fun displayOrder(session: GameSession): List<String> {
        val joinOrder = resolvedJoinOrder(session)
        if (joinOrder.isNotEmpty()) return joinOrder
        return session.players.keys.sorted()
    }

    fun resolvedJoinOrder(session: GameSession): List<String> {
        if (session.playerJoinOrder.isNotEmpty()) {
            return session.playerJoinOrder.filter { session.players.containsKey(it) }
        }
        val turnOrder = session.turnState?.turnOrder
        if (turnOrder != null && turnOrder.isNotEmpty()) {
            return turnOrder.filter { session.players.containsKey(it) }
        }
        return session.players.keys.sorted()
    }

    fun normalize(session: GameSession): GameSession {
        val derived = resolvedJoinOrder(session)
        if (derived.isEmpty()) return session
        val withJoinOrder = if (session.playerJoinOrder == derived) {
            session
        } else {
            session.copy(playerJoinOrder = derived)
        }
        val turnState = withJoinOrder.turnState
        if (turnState != null && turnState.turnOrder != derived) {
            return withJoinOrder.copy(
                turnState = turnState.copy(turnOrder = derived),
            )
        }
        return withJoinOrder
    }
}
