package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ColorGroupState(
    val colorGroup: String,
    val completionBonusApplied: Boolean = false,
)
