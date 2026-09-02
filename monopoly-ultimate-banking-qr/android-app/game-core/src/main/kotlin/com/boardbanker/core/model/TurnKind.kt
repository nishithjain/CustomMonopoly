package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TurnKind {
    NORMAL,
    EXTRA,
}
