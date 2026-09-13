package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshot(
    val players: Map<String, PlayerState>,
    val playerJoinOrder: List<String> = emptyList(),
    val properties: Map<String, PropertyState>,
    val energyGrids: Map<String, EnergyGridState> = emptyMap(),
    val colorGroups: Map<String, ColorGroupState>,
    val temporaryEffects: List<TemporaryEffect>,
    val debtResolution: DebtResolutionState? = null,
    val auction: AuctionState? = null,
    val pendingEventChoice: PendingEventChoice? = null,
    val pendingEventExecution: PendingEventExecution? = null,
    val pendingEventResolution: EventResolution? = null,
    val pendingEventMultiContributorSettlement: PendingEventMultiContributorSettlement? = null,
    val pendingEventDraw: PendingEventDraw? = null,
    val pendingDiceGamble: PendingDiceGamble? = null,
    val pendingEnergyGridLanding: PendingEnergyGridLanding? = null,
    val eventChainDepth: Int = 0,
    val turnState: TurnState? = null,
    val status: GameStatus = GameStatus.ACTIVE,
)
