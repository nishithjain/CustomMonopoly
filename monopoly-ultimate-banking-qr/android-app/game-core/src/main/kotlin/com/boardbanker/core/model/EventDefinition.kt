package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventDefinition(
    val eventId: String,
    val name: String,
    val qrPayload: String,
    val eventSubtitle: String = "",
    val eventDescription: String = "",
    val engineRule: EventEngineRule,
) {
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
