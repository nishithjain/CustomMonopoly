package com.boardbanker.core.model

import kotlinx.serialization.Serializable

typealias EventEngineRule = EventActionDefinition

@Serializable
data class EventDefinition(
    val eventId: String,
    val deckId: String = "main",
    val name: String,
    val qrPayload: String,
    val eventSubtitle: String = "",
    val eventDescription: String = "",
    val actions: List<EventActionDefinition>,
) {
    val engineRule: EventActionDefinition
        get() = actions.first()

    fun displayText(): String = buildString {
        if (eventSubtitle.isNotBlank()) {
            append(eventSubtitle)
            if (eventDescription.isNotBlank()) {
                append("\n\n")
            }
        }
        append(eventDescription)
    }
}
