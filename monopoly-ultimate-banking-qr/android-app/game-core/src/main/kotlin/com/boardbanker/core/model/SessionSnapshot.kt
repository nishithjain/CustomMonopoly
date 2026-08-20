package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshot(
    val players: Map<String, PlayerState>,
    val properties: Map<String, PropertyState>,
    val colorGroups: Map<String, ColorGroupState>,
    val temporaryEffects: List<TemporaryEffect>,
    val debtResolution: DebtResolutionState? = null,
    val auction: AuctionState? = null,
    val pendingEventChoice: PendingEventChoice? = null,
    val status: GameStatus = GameStatus.ACTIVE,
)
