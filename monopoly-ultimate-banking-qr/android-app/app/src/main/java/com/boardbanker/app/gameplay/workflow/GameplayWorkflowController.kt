package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PendingEventExecution
import com.boardbanker.core.event.EventInstructionFormatter
import com.boardbanker.core.model.displayNameWithNumber
import com.boardbanker.core.model.EnergyGridDisplayNames
import com.boardbanker.core.rules.EnergyGridRentCalculator
import com.boardbanker.core.rules.JailGameplayGuard

sealed class GameplayWorkflowState {
    data object Ready : GameplayWorkflowState()

    data class LocationWaitingForDestinationProperty(
        val playerId: String,
    ) : GameplayWorkflowState()

    data class PropertySummary(
        val propertyId: String,
        val propertyName: String,
        val ownerName: String?,
        val isUnowned: Boolean,
        val purchasePrice: Int?,
        val rentLevel: Int,
        val currentRent: Int?,
        val maximumRentLevel: Int,
    ) : GameplayWorkflowState()

    data class UnownedPropertyDecision(
        val propertyId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class WaitingForPurchasingPlayer(
        val propertyId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class WaitingForRentPayer(
        val propertyId: String,
        val ownerPlayerId: String?,
        val ownerName: String?,
    ) : GameplayWorkflowState()

    data class EventIntro(
        val eventId: String,
        val eventName: String,
        val eventSubtitle: String,
        val eventDescription: String,
    ) : GameplayWorkflowState()

    data class EventCollectingTargets(
        val eventId: String,
        val actionIndex: Int,
        val plan: EventWorkflowPlan,
        val stepIndex: Int,
        val actingPlayerId: String? = null,
        val targetPlayerId: String? = null,
        val propertyId: String? = null,
        val secondPropertyId: String? = null,
    ) : GameplayWorkflowState()

    data class EventConfirm(
        val eventId: String,
        val actingPlayerId: String,
        val targetPlayerId: String?,
        val propertyId: String?,
        val secondPropertyId: String?,
    ) : GameplayWorkflowState()

    data class EventPropertyChoice(
        val eventId: String,
        val actingPlayerId: String,
        val propertyId: String,
    ) : GameplayWorkflowState()

    data class EventDiceGamble(
        val eventId: String,
        val actingPlayerId: String,
    ) : GameplayWorkflowState()

    data class EventDrawScanRequired(
        val parentEventId: String,
        val actingPlayerId: String,
        val chainDepth: Int,
        val maximumChainDepth: Int,
    ) : GameplayWorkflowState()

    data class WaitingForAuctionStarter(
        val propertyId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class EnergyGridSummary(
        val energyGridId: String,
        val energyGridName: String,
        val ownerName: String?,
        val isUnowned: Boolean,
        val purchasePrice: Int?,
        val rentTable: List<Pair<Int, Int>>,
        val currentRent: Int?,
    ) : GameplayWorkflowState()

    data class UnownedEnergyGridDecision(
        val energyGridId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class WaitingForPurchasingPlayerEnergyGrid(
        val energyGridId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class WaitingForRentPayerEnergyGrid(
        val energyGridId: String,
        val ownerPlayerId: String?,
        val ownerName: String?,
    ) : GameplayWorkflowState()

    data class WaitingForAuctionStarterEnergyGrid(
        val energyGridId: String,
        val landingPlayerId: String? = null,
    ) : GameplayWorkflowState()

    data class WaitingForExpectedEnergyGridScan(
        val energyGridId: String,
        val actingPlayerId: String,
    ) : GameplayWorkflowState()

    data class PlayerInfo(val playerId: String) : GameplayWorkflowState()

    data class Error(val message: String) : GameplayWorkflowState()
}

data class WorkflowScanRequest(
    val scanRequest: ScanRequest,
) {
    val expectedCardType: CardType
        get() = scanRequest.singleExpectedType ?: CardType.USER
    val prompt: String
        get() = scanRequest.instruction
}

data class WorkflowCommandRequest(
    val command: GameCommand,
    val context: WorkflowCommandContext,
)

sealed class WorkflowCommandContext {
    data class Purchase(val playerId: String, val propertyId: String, val balanceBefore: Int) :
        WorkflowCommandContext()

    data class EnergyGridPurchase(val playerId: String, val energyGridId: String, val balanceBefore: Int) :
        WorkflowCommandContext()

    data class PropertyLanding(val playerId: String, val propertyId: String) : WorkflowCommandContext()

    data class EnergyGridLanding(val playerId: String, val energyGridId: String) : WorkflowCommandContext()

    data class ApplyEvent(val eventId: String) : WorkflowCommandContext()

    data class EventChoice(
        val eventId: String,
        val actingPlayerId: String,
        val propertyId: String,
        val choice: GameCommand.EventPropertyChoiceType,
    ) : WorkflowCommandContext()

    data class RollEventDice(val eventId: String) : WorkflowCommandContext()

    data class ResolvePendingEventDraw(val eventId: String) : WorkflowCommandContext()
}

sealed class WorkflowAction {
    data class StateChanged(val state: GameplayWorkflowState) : WorkflowAction()
    data class RequestScan(val request: WorkflowScanRequest) : WorkflowAction()
    data class ExecuteCommand(val request: WorkflowCommandRequest) : WorkflowAction()
    data class NavigateToAuction(
        val propertyId: String? = null,
        val energyGridId: String? = null,
        val startedByPlayerId: String,
    ) : WorkflowAction()
    data object Cancelled : WorkflowAction()
    data class WrongCardType(val expected: CardType, val message: String) : WorkflowAction()
}

class GameplayWorkflowController(
    private val definitions: GameDefinitions,
) {
    private var state: GameplayWorkflowState = GameplayWorkflowState.Ready
    private var buyLocked = false
    private var eventContinueLocked = false

    fun currentState(): GameplayWorkflowState = state

    fun reset() {
        state = GameplayWorkflowState.Ready
        buyLocked = false
        eventContinueLocked = false
    }

    fun onPropertyScanned(propertyId: String, session: GameSession): List<WorkflowAction> {
        propertyPurchaseBlockedForActivePlayer(session)?.let { message ->
            state = GameplayWorkflowState.Error(message)
            return listOf(WorkflowAction.StateChanged(state))
        }
        val propertyDef = definitions.properties[propertyId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown property.")),
        )
        return beginPropertyWorkflow(propertyId, session, landingPlayerId = null)
    }

    fun onEnergyGridScanned(energyGridId: String, session: GameSession): List<WorkflowAction> {
        val pending = session.pendingEnergyGridLanding
        if (pending != null && pending.energyGridId != energyGridId) {
            return listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.ENERGY_GRID,
                    message = "ENERGY GRID CARD EXPECTED\n\nPlease scan ${EnergyGridDisplayNames.displayNameWithNumber(pending.energyGridId, definitions)}.",
                ),
            )
        }
        val landingPlayerId = pending?.actingPlayerId
        propertyPurchaseBlockedForActivePlayer(session)?.let { message ->
            if (landingPlayerId == null || landingPlayerId == session.turnState?.activePlayerId) {
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
        }
        if (definitions.energyGrids[energyGridId] == null) {
            return listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown energy grid.")))
        }
        return beginEnergyGridWorkflow(energyGridId, session, landingPlayerId)
    }

    fun beginPendingEnergyGridLanding(session: GameSession): List<WorkflowAction> {
        val pending = session.pendingEnergyGridLanding ?: return emptyList()
        val gridName = EnergyGridDisplayNames.displayNameWithNumber(pending.energyGridId, definitions)
        state = GameplayWorkflowState.WaitingForExpectedEnergyGridScan(
            energyGridId = pending.energyGridId,
            actingPlayerId = pending.actingPlayerId,
        )
        return listOf(
            WorkflowAction.StateChanged(state),
            WorkflowAction.RequestScan(
                WorkflowScanRequest(ScanRequest.energyGrid(pending.energyGridId, gridName)),
            ),
        )
    }

    private fun beginEnergyGridWorkflow(
        energyGridId: String,
        session: GameSession,
        landingPlayerId: String?,
    ): List<WorkflowAction> {
        val gridDef = definitions.energyGrids[energyGridId]!!
        val gridState = session.energyGrids[energyGridId]
        val ownerId = gridState?.ownerPlayerId
        val ownerName = ownerId?.let { PlayerDisplayNames.displayName(session, it, definitions) }
        val rentTable = gridDef.rentLevels.sortedBy { it.ownedCount }.map { it.ownedCount to it.amount }
        val currentRent = ownerId?.let {
            EnergyGridRentCalculator.rentForOwner(definitions, session, it)
        }

        if (landingPlayerId != null) {
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, landingPlayerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
            return when (ownerId) {
                null -> {
                    state = GameplayWorkflowState.UnownedEnergyGridDecision(energyGridId, landingPlayerId)
                    listOf(
                        WorkflowAction.StateChanged(showUnownedEnergyGrid(energyGridId)),
                        WorkflowAction.StateChanged(state),
                    )
                }
                landingPlayerId -> listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessEnergyGridLanding(landingPlayerId, energyGridId),
                            context = WorkflowCommandContext.EnergyGridLanding(
                                playerId = landingPlayerId,
                                energyGridId = energyGridId,
                            ),
                        ),
                    ),
                )
                else -> listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessEnergyGridLanding(landingPlayerId, energyGridId),
                            context = WorkflowCommandContext.EnergyGridLanding(
                                playerId = landingPlayerId,
                                energyGridId = energyGridId,
                            ),
                        ),
                    ),
                )
            }
        }

        return if (ownerId == null) {
            session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }?.let { activePlayerId ->
                JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, activePlayerId)?.let { message ->
                    state = GameplayWorkflowState.Error(message)
                    return listOf(WorkflowAction.StateChanged(state))
                }
            }
            state = GameplayWorkflowState.UnownedEnergyGridDecision(energyGridId)
            listOf(
                WorkflowAction.StateChanged(showUnownedEnergyGrid(energyGridId)),
                WorkflowAction.StateChanged(state),
            )
        } else {
            state = GameplayWorkflowState.WaitingForRentPayerEnergyGrid(
                energyGridId = energyGridId,
                ownerPlayerId = ownerId,
                ownerName = ownerName,
            )
            listOf(
                WorkflowAction.StateChanged(
                    GameplayWorkflowState.EnergyGridSummary(
                        energyGridId = energyGridId,
                        energyGridName = EnergyGridDisplayNames.displayNameWithNumber(energyGridId, definitions),
                        ownerName = ownerName,
                        isUnowned = false,
                        purchasePrice = gridDef.purchasePrice,
                        rentTable = rentTable,
                        currentRent = currentRent,
                    ),
                ),
                WorkflowAction.StateChanged(state),
            )
        }
    }

    fun onBuyEnergyGridSelected(session: GameSession): List<WorkflowAction> {
        if (buyLocked) return emptyList()
        val current = state
        if (current !is GameplayWorkflowState.UnownedEnergyGridDecision) return emptyList()
        val landingPlayerId = current.landingPlayerId
            ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        landingPlayerId?.let { playerId ->
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
        }
        if (landingPlayerId != null) {
            buyLocked = true
            val balanceBefore = session.players[landingPlayerId]?.balance ?: 0
            return listOf(
                WorkflowAction.ExecuteCommand(
                    WorkflowCommandRequest(
                        command = GameCommand.PurchaseEnergyGrid(landingPlayerId, current.energyGridId),
                        context = WorkflowCommandContext.EnergyGridPurchase(
                            playerId = landingPlayerId,
                            energyGridId = current.energyGridId,
                            balanceBefore = balanceBefore,
                        ),
                    ),
                ),
            )
        }
        buyLocked = true
        state = GameplayWorkflowState.WaitingForPurchasingPlayerEnergyGrid(current.energyGridId)
        return listOf(
            WorkflowAction.StateChanged(state),
            WorkflowAction.RequestScan(WorkflowScanRequest(ScanRequest.player())),
        )
    }

    fun onAuctionEnergyGridSelected(session: GameSession): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.UnownedEnergyGridDecision) return emptyList()
        val landingPlayerId = current.landingPlayerId
            ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        landingPlayerId?.let { playerId ->
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
        }
        if (landingPlayerId != null) {
            return listOf(
                WorkflowAction.NavigateToAuction(
                    energyGridId = current.energyGridId,
                    startedByPlayerId = landingPlayerId,
                ),
            )
        }
        state = GameplayWorkflowState.WaitingForAuctionStarterEnergyGrid(current.energyGridId)
        return listOf(
            WorkflowAction.StateChanged(state),
            WorkflowAction.RequestScan(WorkflowScanRequest(ScanRequest.player())),
        )
    }

    fun showUnownedEnergyGrid(energyGridId: String): GameplayWorkflowState {
        val gridDef = definitions.energyGrids[energyGridId]!!
        val rentTable = gridDef.rentLevels.sortedBy { it.ownedCount }.map { it.ownedCount to it.amount }
        return GameplayWorkflowState.EnergyGridSummary(
            energyGridId = energyGridId,
            energyGridName = EnergyGridDisplayNames.displayNameWithNumber(energyGridId, definitions),
            ownerName = null,
            isUnowned = true,
            purchasePrice = gridDef.purchasePrice,
            rentTable = rentTable,
            currentRent = rentTable.firstOrNull()?.second,
        )
    }

    fun beginLocationDestinationProperty(
        playerId: String,
        propertyId: String,
        session: GameSession,
    ): List<WorkflowAction> {
        JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
            state = GameplayWorkflowState.Error(message)
            return listOf(WorkflowAction.StateChanged(state))
        }
        if (definitions.properties[propertyId] == null) {
            return listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown property.")))
        }
        return beginPropertyWorkflow(propertyId, session, landingPlayerId = playerId)
    }

    private fun beginPropertyWorkflow(
        propertyId: String,
        session: GameSession,
        landingPlayerId: String?,
    ): List<WorkflowAction> {
        val propertyDef = definitions.properties[propertyId]!!
        val propertyState = session.properties[propertyId]
        val ownerId = propertyState?.ownerPlayerId
        val ownerName = ownerId?.let { PlayerDisplayNames.displayName(session, it, definitions) }
        val rentLevel = propertyState?.currentRentLevel ?: propertyDef.initialRentLevel
        val currentRent = propertyDef.rentLevels.firstOrNull { it.level == rentLevel }?.amount

        if (landingPlayerId != null) {
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, landingPlayerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
            return when (ownerId) {
                null -> {
                    state = GameplayWorkflowState.UnownedPropertyDecision(propertyId, landingPlayerId)
                    listOf(
                        WorkflowAction.StateChanged(showUnownedProperty(propertyId)),
                        WorkflowAction.StateChanged(state),
                    )
                }
                landingPlayerId -> listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessPropertyLanding(landingPlayerId, propertyId),
                            context = WorkflowCommandContext.PropertyLanding(
                                playerId = landingPlayerId,
                                propertyId = propertyId,
                            ),
                        ),
                    ),
                )
                else -> listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessPropertyLanding(landingPlayerId, propertyId),
                            context = WorkflowCommandContext.PropertyLanding(
                                playerId = landingPlayerId,
                                propertyId = propertyId,
                            ),
                        ),
                    ),
                )
            }
        }

        return if (ownerId == null) {
            session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }?.let { activePlayerId ->
                JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, activePlayerId)?.let { message ->
                    state = GameplayWorkflowState.Error(message)
                    return listOf(WorkflowAction.StateChanged(state))
                }
            }
            state = GameplayWorkflowState.UnownedPropertyDecision(propertyId)
            listOf(
                WorkflowAction.StateChanged(showUnownedProperty(propertyId)),
                WorkflowAction.StateChanged(state),
            )
        } else {
            state = GameplayWorkflowState.WaitingForRentPayer(
                propertyId = propertyId,
                ownerPlayerId = ownerId,
                ownerName = ownerName,
            )
            listOf(
                WorkflowAction.StateChanged(
                    GameplayWorkflowState.PropertySummary(
                        propertyId = propertyId,
                        propertyName = propertyDef.displayNameWithNumber(),
                        ownerName = ownerName,
                        isUnowned = false,
                        purchasePrice = propertyDef.purchasePrice,
                        rentLevel = rentLevel,
                        currentRent = currentRent,
                        maximumRentLevel = propertyDef.maximumRentLevel,
                    ),
                ),
                WorkflowAction.StateChanged(state),
            )
        }
    }

    fun onEventScanned(eventId: String, session: GameSession): List<WorkflowAction> {
        activeJailedPlayerBoardActionMessage(session)?.let { message ->
            state = GameplayWorkflowState.Error(message)
            return listOf(WorkflowAction.StateChanged(state))
        }
        val event = definitions.events[eventId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown event.")),
        )
        state = GameplayWorkflowState.EventIntro(
            eventId = eventId,
            eventName = event.name,
            eventSubtitle = event.eventSubtitle,
            eventDescription = EventInstructionFormatter.formatDescription(event, definitions),
        )
        return listOf(
            WorkflowAction.StateChanged(state),
        )
    }

    fun onEventContinue(session: GameSession): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.EventIntro || eventContinueLocked) return emptyList()
        return beginEventActionCollection(
            eventId = current.eventId,
            actionIndex = 0,
            actingPlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() },
        )
    }

    fun resumePendingEventExecution(session: GameSession): List<WorkflowAction> {
        val pending = session.pendingEventExecution ?: return emptyList()
        return beginEventActionCollection(
            eventId = pending.eventId,
            actionIndex = pending.currentActionIndex,
            actingPlayerId = pending.actingPlayerId,
            targetPlayerId = pending.targetPlayerId,
            propertyId = pending.propertyId,
            secondPropertyId = pending.secondPropertyId,
        )
    }

    fun restoreWorkflowFromSession(session: GameSession): List<WorkflowAction> {
        if (isIncompatiblePropertyWorkflowForJailedPlayer(state, session)) {
            reset()
            return emptyList()
        }
        if (activeJailedPlayerBoardActionMessage(session) != null) {
            reset()
            return emptyList()
        }
        session.pendingDiceGamble?.let { pending ->
            state = GameplayWorkflowState.EventDiceGamble(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
            )
            return listOf(WorkflowAction.StateChanged(state))
        }
        session.pendingEventDraw?.let { pending ->
            return enterEventDrawScan(pending.parentEventId, pending.actingPlayerId, pending.chainDepth, pending.maximumChainDepth)
        }
        session.pendingEventChoice?.let { choice ->
            state = GameplayWorkflowState.EventPropertyChoice(
                eventId = choice.eventId,
                actingPlayerId = choice.actingPlayerId,
                propertyId = choice.propertyId,
            )
            return listOf(WorkflowAction.StateChanged(state))
        }
        session.pendingEventExecution?.let { pending ->
            return resumePendingEventExecution(session)
        }
        session.pendingEnergyGridLanding?.let {
            return beginPendingEnergyGridLanding(session)
        }
        return emptyList()
    }

    fun hasMandatoryEventActionPending(): Boolean =
        state is GameplayWorkflowState.EventDiceGamble ||
            state is GameplayWorkflowState.EventDrawScanRequired ||
            state is GameplayWorkflowState.EventCollectingTargets ||
            state is GameplayWorkflowState.EventConfirm ||
            state is GameplayWorkflowState.EventPropertyChoice

    fun enterEventDrawScan(
        parentEventId: String,
        actingPlayerId: String,
        chainDepth: Int,
        maximumChainDepth: Int,
    ): List<WorkflowAction> {
        state = GameplayWorkflowState.EventDrawScanRequired(
            parentEventId = parentEventId,
            actingPlayerId = actingPlayerId,
            chainDepth = chainDepth,
            maximumChainDepth = maximumChainDepth,
        )
        return listOf(WorkflowAction.StateChanged(state))
    }

    fun onPendingEventDrawScanned(eventId: String, session: GameSession): List<WorkflowAction> {
        val pending = session.pendingEventDraw ?: return emptyList()
        if (definitions.events[eventId] == null) {
            return listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.EVENT,
                    message = "EVENT CARD EXPECTED\n\nScan an Event card from this edition.",
                ),
            )
        }
        return listOf(
            WorkflowAction.ExecuteCommand(
                WorkflowCommandRequest(
                    command = GameCommand.ResolvePendingEventDraw(
                        eventId = eventId,
                        actingPlayerId = pending.actingPlayerId,
                    ),
                    context = WorkflowCommandContext.ResolvePendingEventDraw(eventId),
                ),
            ),
        )
    }

    fun enterDiceGamble(eventId: String, actingPlayerId: String): List<WorkflowAction> {
        state = GameplayWorkflowState.EventDiceGamble(eventId, actingPlayerId)
        return listOf(WorkflowAction.StateChanged(state))
    }

    private fun beginEventActionCollection(
        eventId: String,
        actionIndex: Int,
        actingPlayerId: String? = null,
        targetPlayerId: String? = null,
        propertyId: String? = null,
        secondPropertyId: String? = null,
    ): List<WorkflowAction> {
        val event = definitions.events[eventId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown event.")),
        )
        if (actionIndex !in event.actions.indices) {
            return listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Error("Invalid event action index.")))
        }
        val plan = EventWorkflowPlanner.planForEventAtAction(event, actionIndex)
        val startStep = EventWorkflowPlanner.initialStepIndex(
            plan = plan,
            actingPlayerId = actingPlayerId,
            targetPlayerId = targetPlayerId,
            propertyId = propertyId,
            secondPropertyId = secondPropertyId,
        )
        val collecting = GameplayWorkflowState.EventCollectingTargets(
            eventId = eventId,
            actionIndex = actionIndex,
            plan = plan,
            stepIndex = startStep,
            actingPlayerId = actingPlayerId,
            targetPlayerId = targetPlayerId,
            propertyId = propertyId,
            secondPropertyId = secondPropertyId,
        )
        if (startStep >= plan.steps.size) {
            eventContinueLocked = true
            return executeApplyEvent(collecting)
        }
        state = collecting
        return listOf(
            WorkflowAction.StateChanged(collecting),
            scanRequestForEventStep(collecting),
        )
    }

    fun onUserScanned(playerId: String, session: GameSession): List<WorkflowAction> {
        if (definitions.players[playerId] == null) {
            return listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown player.")))
        }
        return when (val current = state) {
            is GameplayWorkflowState.Ready -> {
                state = GameplayWorkflowState.PlayerInfo(playerId)
                listOf(WorkflowAction.StateChanged(state))
            }
            is GameplayWorkflowState.WaitingForPurchasingPlayerEnergyGrid -> {
                JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                    state = GameplayWorkflowState.Error(message)
                    return listOf(WorkflowAction.StateChanged(state))
                }
                val balanceBefore = session.players[playerId]?.balance ?: 0
                listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.PurchaseEnergyGrid(playerId, current.energyGridId),
                            context = WorkflowCommandContext.EnergyGridPurchase(
                                playerId = playerId,
                                energyGridId = current.energyGridId,
                                balanceBefore = balanceBefore,
                            ),
                        ),
                    ),
                )
            }
            is GameplayWorkflowState.WaitingForRentPayerEnergyGrid -> {
                listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessEnergyGridLanding(playerId, current.energyGridId),
                            context = WorkflowCommandContext.EnergyGridLanding(
                                playerId = playerId,
                                energyGridId = current.energyGridId,
                            ),
                        ),
                    ),
                )
            }
            is GameplayWorkflowState.WaitingForAuctionStarterEnergyGrid -> {
                listOf(
                    WorkflowAction.NavigateToAuction(
                        energyGridId = current.energyGridId,
                        startedByPlayerId = playerId,
                    ),
                )
            }
            is GameplayWorkflowState.WaitingForExpectedEnergyGridScan -> listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.ENERGY_GRID,
                    message = "ENERGY GRID CARD EXPECTED\n\nPlease scan ${EnergyGridDisplayNames.displayNameWithNumber(current.energyGridId, definitions)}.",
                ),
            )
            is GameplayWorkflowState.WaitingForPurchasingPlayer -> {
                JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                    state = GameplayWorkflowState.Error(message)
                    return listOf(WorkflowAction.StateChanged(state))
                }
                val balanceBefore = session.players[playerId]?.balance ?: 0
                listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.PurchaseProperty(playerId, current.propertyId),
                            context = WorkflowCommandContext.Purchase(
                                playerId = playerId,
                                propertyId = current.propertyId,
                                balanceBefore = balanceBefore,
                            ),
                        ),
                    ),
                )
            }
            is GameplayWorkflowState.WaitingForRentPayer -> {
                listOf(
                    WorkflowAction.ExecuteCommand(
                        WorkflowCommandRequest(
                            command = GameCommand.ProcessPropertyLanding(playerId, current.propertyId),
                            context = WorkflowCommandContext.PropertyLanding(
                                playerId = playerId,
                                propertyId = current.propertyId,
                            ),
                        ),
                    ),
                )
            }
            is GameplayWorkflowState.WaitingForAuctionStarter -> {
                listOf(
                    WorkflowAction.NavigateToAuction(
                        propertyId = current.propertyId,
                        startedByPlayerId = playerId,
                    ),
                )
            }
            is GameplayWorkflowState.EventCollectingTargets -> handleEventUserScan(current, playerId)
            is GameplayWorkflowState.LocationWaitingForDestinationProperty -> listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.PROPERTY,
                    message = "PROPERTY CARD EXPECTED\n\nPlease scan the destination Property card.",
                ),
            )
            else -> listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.USER,
                    message = "PLAYER CARD EXPECTED\n\nPlease scan a Player card.",
                ),
            )
        }
    }

    fun onBuySelected(session: GameSession): List<WorkflowAction> {
        if (buyLocked) return emptyList()
        val current = state
        if (current !is GameplayWorkflowState.UnownedPropertyDecision) return emptyList()
        val landingPlayerId = current.landingPlayerId
            ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        landingPlayerId?.let { playerId ->
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
        }
        if (landingPlayerId != null) {
            buyLocked = true
            val balanceBefore = session.players[landingPlayerId]?.balance ?: 0
            return listOf(
                WorkflowAction.ExecuteCommand(
                    WorkflowCommandRequest(
                        command = GameCommand.PurchaseProperty(landingPlayerId, current.propertyId),
                        context = WorkflowCommandContext.Purchase(
                            playerId = landingPlayerId,
                            propertyId = current.propertyId,
                            balanceBefore = balanceBefore,
                        ),
                    ),
                ),
            )
        }
        buyLocked = true
        state = GameplayWorkflowState.WaitingForPurchasingPlayer(current.propertyId)
        return listOf(
            WorkflowAction.StateChanged(state),
            WorkflowAction.RequestScan(
                WorkflowScanRequest(ScanRequest.player()),
            ),
        )
    }

    fun onAuctionSelected(session: GameSession): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.UnownedPropertyDecision) return emptyList()
        val landingPlayerId = current.landingPlayerId
            ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        landingPlayerId?.let { playerId ->
            JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, playerId)?.let { message ->
                state = GameplayWorkflowState.Error(message)
                return listOf(WorkflowAction.StateChanged(state))
            }
        }
        if (landingPlayerId != null) {
            return listOf(
                WorkflowAction.NavigateToAuction(
                    propertyId = current.propertyId,
                    startedByPlayerId = landingPlayerId,
                ),
            )
        }
        state = GameplayWorkflowState.WaitingForAuctionStarter(current.propertyId)
        return listOf(
            WorkflowAction.StateChanged(state),
            WorkflowAction.RequestScan(
                WorkflowScanRequest(ScanRequest.player()),
            ),
        )
    }

    fun enterLocationWaitingForDestination(playerId: String): List<WorkflowAction> {
        state = GameplayWorkflowState.LocationWaitingForDestinationProperty(playerId)
        return listOf(WorkflowAction.StateChanged(state))
    }

    fun onEventConfirm(): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.EventConfirm) return emptyList()
        return listOf(
            WorkflowAction.ExecuteCommand(
                WorkflowCommandRequest(
                    command = EventWorkflowPlanner.buildApplyCommand(
                        eventId = current.eventId,
                        actingPlayerId = current.actingPlayerId,
                        targetPlayerId = current.targetPlayerId,
                        propertyId = current.propertyId,
                        secondPropertyId = current.secondPropertyId,
                    ),
                    context = WorkflowCommandContext.ApplyEvent(current.eventId),
                ),
            ),
        )
    }

    fun onEventChoice(choice: GameCommand.EventPropertyChoiceType): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.EventPropertyChoice) return emptyList()
        if (choice == GameCommand.EventPropertyChoiceType.AUCTION) {
            state = GameplayWorkflowState.WaitingForAuctionStarter(current.propertyId)
            return listOf(
                WorkflowAction.StateChanged(state),
                WorkflowAction.RequestScan(
                    WorkflowScanRequest(ScanRequest.player()),
                ),
            )
        }
        return listOf(
            WorkflowAction.ExecuteCommand(
                WorkflowCommandRequest(
                    command = GameCommand.EventPropertyChoice(
                        actingPlayerId = current.actingPlayerId,
                        propertyId = current.propertyId,
                        choice = choice,
                    ),
                    context = WorkflowCommandContext.EventChoice(
                        eventId = current.eventId,
                        actingPlayerId = current.actingPlayerId,
                        propertyId = current.propertyId,
                        choice = choice,
                    ),
                ),
            ),
        )
    }

    fun onEventPropertyScanned(propertyId: String): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.EventCollectingTargets) {
            return listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.PROPERTY,
                    message = "PROPERTY CARD EXPECTED\n\nPlease scan a Property card.",
                ),
            )
        }
        val step = current.plan.steps.getOrNull(current.stepIndex)
        if (step != EventScanStep.PROPERTY && step != EventScanStep.SECOND_PROPERTY) {
            return listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.PROPERTY,
                    message = "PROPERTY CARD EXPECTED\n\nPlease scan a Property card.",
                ),
            )
        }
        val updated = when (step) {
            EventScanStep.PROPERTY -> current.copy(propertyId = propertyId, stepIndex = current.stepIndex + 1)
            EventScanStep.SECOND_PROPERTY -> current.copy(secondPropertyId = propertyId, stepIndex = current.stepIndex + 1)
            else -> current
        }
        return advanceEventWorkflow(updated)
    }

    fun onCancel(): List<WorkflowAction> {
        reset()
        return listOf(WorkflowAction.Cancelled)
    }

    fun onDone(): List<WorkflowAction> {
        reset()
        return listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Ready))
    }

    fun onCommandSucceeded(resultContext: WorkflowCommandContext, session: GameSession) {
        buyLocked = false
        eventContinueLocked = false
        when (resultContext) {
            is WorkflowCommandContext.ApplyEvent -> {
                when {
                    session.pendingEventChoice != null -> Unit
                    session.pendingEventExecution != null -> Unit
                    session.pendingDiceGamble != null -> Unit
                    session.pendingEventDraw != null -> Unit
                    else -> reset()
                }
            }
            is WorkflowCommandContext.ResolvePendingEventDraw -> {
                when {
                    session.pendingEventChoice != null -> Unit
                    session.pendingEventExecution != null -> Unit
                    session.pendingDiceGamble != null -> Unit
                    session.pendingEventDraw != null -> Unit
                    else -> reset()
                }
            }
            is WorkflowCommandContext.EventChoice -> {
                when {
                    session.pendingEventExecution != null -> Unit
                    else -> reset()
                }
            }
            else -> reset()
        }
    }

    fun onCommandFailed() {
        buyLocked = false
        eventContinueLocked = false
    }

    fun showUnownedProperty(propertyId: String): GameplayWorkflowState {
        val propertyDef = definitions.properties[propertyId]!!
        return GameplayWorkflowState.PropertySummary(
            propertyId = propertyId,
            propertyName = propertyDef.displayNameWithNumber(),
            ownerName = null,
            isUnowned = true,
            purchasePrice = propertyDef.purchasePrice,
            rentLevel = propertyDef.initialRentLevel,
            currentRent = propertyDef.rentLevels.firstOrNull { it.level == 1 }?.amount,
            maximumRentLevel = propertyDef.maximumRentLevel,
        )
    }

    fun beginEventPropertyChoice(eventId: String, actingPlayerId: String, propertyId: String) {
        state = GameplayWorkflowState.EventPropertyChoice(eventId, actingPlayerId, propertyId)
    }

    private fun handleEventUserScan(
        current: GameplayWorkflowState.EventCollectingTargets,
        playerId: String,
    ): List<WorkflowAction> {
        val step = current.plan.steps.getOrNull(current.stepIndex)
        val updated = when (step) {
            EventScanStep.ACTING_PLAYER -> current.copy(actingPlayerId = playerId, stepIndex = current.stepIndex + 1)
            EventScanStep.TARGET_PLAYER -> current.copy(targetPlayerId = playerId, stepIndex = current.stepIndex + 1)
            else -> return listOf(
                WorkflowAction.WrongCardType(
                    expected = CardType.USER,
                    message = "PLAYER CARD EXPECTED\n\nPlease scan a Player card.",
                ),
            )
        }
        return advanceEventWorkflow(updated)
    }

    private fun advanceEventWorkflow(current: GameplayWorkflowState.EventCollectingTargets): List<WorkflowAction> {
        val nextStep = current.plan.steps.getOrNull(current.stepIndex)
        if (nextStep == EventScanStep.CONFIRM) {
            val acting = current.actingPlayerId ?: return error("Missing acting player.")
            state = GameplayWorkflowState.EventConfirm(
                eventId = current.eventId,
                actingPlayerId = acting,
                targetPlayerId = current.targetPlayerId,
                propertyId = current.propertyId,
                secondPropertyId = current.secondPropertyId,
            )
            return listOf(WorkflowAction.StateChanged(state))
        }
        if (nextStep == null) {
            return executeApplyEvent(current)
        }
        state = current
        return listOf(
            WorkflowAction.StateChanged(current),
            scanRequestForEventStep(current),
        )
    }

    private fun executeApplyEvent(current: GameplayWorkflowState.EventCollectingTargets): List<WorkflowAction> {
        val acting = current.actingPlayerId ?: return error("Missing acting player.")
        return listOf(
            WorkflowAction.ExecuteCommand(
                WorkflowCommandRequest(
                    command = EventWorkflowPlanner.buildApplyCommand(
                        eventId = current.eventId,
                        actingPlayerId = acting,
                        targetPlayerId = current.targetPlayerId,
                        propertyId = current.propertyId,
                        secondPropertyId = current.secondPropertyId,
                    ),
                    context = WorkflowCommandContext.ApplyEvent(current.eventId),
                ),
            ),
        )
    }

    private fun scanRequestForEventStep(current: GameplayWorkflowState.EventCollectingTargets): WorkflowAction.RequestScan {
        val step = current.plan.steps[current.stepIndex]
        return WorkflowAction.RequestScan(
            WorkflowScanRequest(EventWorkflowPlanner.scanRequest(step)),
        )
    }

    private fun error(message: String): List<WorkflowAction> =
        listOf(WorkflowAction.StateChanged(GameplayWorkflowState.Error(message)))

    private fun activeJailedPlayerBoardActionMessage(session: GameSession): String? {
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return null
        return JailGameplayGuard.boardActionBlockedMessage(definitions, session, activePlayerId)
    }

    private fun propertyPurchaseBlockedForActivePlayer(session: GameSession): String? {
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return null
        return JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, activePlayerId)
    }

    fun purchaseTargetPlayerId(
        workflow: GameplayWorkflowState,
        session: GameSession,
    ): String? = when (workflow) {
        is GameplayWorkflowState.UnownedPropertyDecision ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        is GameplayWorkflowState.WaitingForPurchasingPlayer ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        is GameplayWorkflowState.WaitingForAuctionStarter ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        is GameplayWorkflowState.UnownedEnergyGridDecision ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        is GameplayWorkflowState.WaitingForPurchasingPlayerEnergyGrid ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        is GameplayWorkflowState.WaitingForAuctionStarterEnergyGrid ->
            workflow.landingPlayerId ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        else -> null
    }

    fun isIncompatiblePropertyWorkflowForJailedPlayer(
        workflow: GameplayWorkflowState,
        session: GameSession,
    ): Boolean {
        if (workflow !is GameplayWorkflowState.UnownedPropertyDecision &&
            workflow !is GameplayWorkflowState.WaitingForPurchasingPlayer &&
            workflow !is GameplayWorkflowState.WaitingForAuctionStarter &&
            workflow !is GameplayWorkflowState.WaitingForRentPayer &&
            workflow !is GameplayWorkflowState.PropertySummary &&
            workflow !is GameplayWorkflowState.UnownedEnergyGridDecision &&
            workflow !is GameplayWorkflowState.WaitingForPurchasingPlayerEnergyGrid &&
            workflow !is GameplayWorkflowState.WaitingForAuctionStarterEnergyGrid &&
            workflow !is GameplayWorkflowState.WaitingForRentPayerEnergyGrid &&
            workflow !is GameplayWorkflowState.EnergyGridSummary
        ) {
            return false
        }
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        if (activePlayerId != null && session.players[activePlayerId]?.jailStatus == true) {
            return true
        }
        val purchaseTarget = purchaseTargetPlayerId(workflow, session)
        return purchaseTarget != null && session.players[purchaseTarget]?.jailStatus == true
    }

    fun jailResolutionGuidance(session: GameSession): String? =
        JailGameplayGuard.activePlayerJailGuidance(definitions, session)
}
