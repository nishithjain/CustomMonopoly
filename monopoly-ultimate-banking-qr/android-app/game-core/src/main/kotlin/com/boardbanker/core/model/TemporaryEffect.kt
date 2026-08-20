package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TemporaryEffect(
    val effectId: String,
    val effectType: String,
    val remainingUses: Int,
    val createdByEventId: String,
    val targetScope: String = "GLOBAL",
    val active: Boolean = true,
)
