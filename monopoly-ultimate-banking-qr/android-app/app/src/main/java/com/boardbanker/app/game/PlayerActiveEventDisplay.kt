package com.boardbanker.app.game

object PlayerActiveEventDisplay {
    fun formatLines(eventNames: List<String>): List<String> = when {
        eventNames.isEmpty() -> emptyList()
        eventNames.size == 1 -> listOf("Event applied: ${eventNames.first()}")
        else -> listOf("Events applied:") + eventNames.map { "• $it" }
    }
}
