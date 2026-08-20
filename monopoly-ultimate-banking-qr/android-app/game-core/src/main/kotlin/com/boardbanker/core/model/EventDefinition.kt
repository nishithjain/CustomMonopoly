package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventDefinition(
    val eventId: String,
    val name: String,
    val qrPayload: String,
    val printedText: String = "",
    val engineRule: EventEngineRule,
)
