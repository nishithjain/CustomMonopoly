package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GameSession(
    val gameId: String,
    val editionId: String,
    val editionDefinitionVersion: Int = EditionIds.LEGACY_DEFINITION_VERSION,
    val status: GameStatus = GameStatus.SETUP,
    val players: Map<String, PlayerState> = emptyMap(),
    val properties: Map<String, PropertyState> = emptyMap(),
    val energyGrids: Map<String, EnergyGridState> = emptyMap(),
    val colorGroups: Map<String, ColorGroupState> = emptyMap(),
    val temporaryEffects: List<TemporaryEffect> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val undoSnapshot: SessionSnapshot? = null,
    val debtResolution: DebtResolutionState? = null,
    val auction: AuctionState? = null,
    val pendingEventChoice: PendingEventChoice? = null,
    val pendingEventExecution: PendingEventExecution? = null,
    val pendingEventDraw: PendingEventDraw? = null,
    val pendingDiceGamble: PendingDiceGamble? = null,
    val pendingEnergyGridLanding: PendingEnergyGridLanding? = null,
    val eventChainDepth: Int = 0,
    val turnState: TurnState? = null,
    val winnerPlayerId: String? = null,
    val transactionCounter: Long = 0,
) {
    fun snapshot(): SessionSnapshot = SessionSnapshot(
        players = players,
        properties = properties,
        energyGrids = energyGrids,
        colorGroups = colorGroups,
        temporaryEffects = temporaryEffects,
        debtResolution = debtResolution,
        auction = auction,
        pendingEventChoice = pendingEventChoice,
        pendingEventExecution = pendingEventExecution,
        pendingEventDraw = pendingEventDraw,
        pendingDiceGamble = pendingDiceGamble,
        pendingEnergyGridLanding = pendingEnergyGridLanding,
        eventChainDepth = eventChainDepth,
        turnState = turnState,
        status = status,
    )

    fun restoreFrom(snapshot: SessionSnapshot): GameSession = copy(
        players = snapshot.players,
        properties = snapshot.properties,
        energyGrids = snapshot.energyGrids,
        colorGroups = snapshot.colorGroups,
        temporaryEffects = snapshot.temporaryEffects,
        debtResolution = snapshot.debtResolution,
        auction = snapshot.auction,
        pendingEventChoice = snapshot.pendingEventChoice,
        pendingEventExecution = snapshot.pendingEventExecution,
        pendingEventDraw = snapshot.pendingEventDraw,
        pendingDiceGamble = snapshot.pendingDiceGamble,
        pendingEnergyGridLanding = snapshot.pendingEnergyGridLanding,
        eventChainDepth = snapshot.eventChainDepth,
        turnState = snapshot.turnState,
        status = snapshot.status,
        undoSnapshot = null,
    )
}
