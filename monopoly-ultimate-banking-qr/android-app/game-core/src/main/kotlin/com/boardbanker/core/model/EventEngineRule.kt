package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EventEngineRule(
    val eventId: String,
    val name: String,
    val actionType: String,
    val targetType: String,
    val requiresPlayerScan: Boolean = false,
    val requiresPropertyScan: Boolean = false,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val amount: Int? = null,
    val temporaryEffect: Boolean = false,
    val physicalActionRequired: Boolean = false,
    val ownedPropertiesOnly: Boolean = true,
)
