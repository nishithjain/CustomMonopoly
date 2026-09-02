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
    val pendingEventExecution: PendingEventExecution? = null,
    val pendingEventDraw: PendingEventDraw? = null,
    val pendingDiceGamble: PendingDiceGamble? = null,
    val eventChainDepth: Int = 0,
    val turnState: TurnState? = null,
    val status: GameStatus = GameStatus.ACTIVE,
)
