package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.displayNameWithNumber

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

    data class WaitingForAuctionStarter(
        val propertyId: String,
        val landingPlayerId: String? = null,
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

    data class PropertyLanding(val playerId: String, val propertyId: String) : WorkflowCommandContext()

    data class ApplyEvent(val eventId: String) : WorkflowCommandContext()

    data class EventChoice(
        val eventId: String,
        val actingPlayerId: String,
        val propertyId: String,
        val choice: GameCommand.EventPropertyChoiceType,
    ) : WorkflowCommandContext()
}

sealed class WorkflowAction {
    data class StateChanged(val state: GameplayWorkflowState) : WorkflowAction()
    data class RequestScan(val request: WorkflowScanRequest) : WorkflowAction()
    data class ExecuteCommand(val request: WorkflowCommandRequest) : WorkflowAction()
    data class NavigateToAuction(val propertyId: String, val startedByPlayerId: String) : WorkflowAction()
    data object Cancelled : WorkflowAction()
    data class WrongCardType(val expected: CardType, val message: String) : WorkflowAction()
}

class GameplayWorkflowController(
    private val definitions: GameDefinitions,
) {
    private var state: GameplayWorkflowState = GameplayWorkflowState.Ready
    private var buyLocked = false

    fun currentState(): GameplayWorkflowState = state

    fun reset() {
        state = GameplayWorkflowState.Ready
        buyLocked = false
    }

    fun onPropertyScanned(propertyId: String, session: GameSession): List<WorkflowAction> {
        val propertyDef = definitions.properties[propertyId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown property.")),
        )
        return beginPropertyWorkflow(propertyId, session, landingPlayerId = null)
    }

    fun beginLocationDestinationProperty(
        playerId: String,
        propertyId: String,
        session: GameSession,
    ): List<WorkflowAction> {
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

    fun onEventScanned(eventId: String): List<WorkflowAction> {
        val event = definitions.events[eventId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown event.")),
        )
        state = GameplayWorkflowState.EventIntro(
            eventId = eventId,
            eventName = event.name,
            eventSubtitle = event.eventSubtitle,
            eventDescription = event.eventDescription,
        )
        return listOf(
            WorkflowAction.StateChanged(state),
        )
    }

    fun onEventContinue(): List<WorkflowAction> {
        val current = state
        if (current !is GameplayWorkflowState.EventIntro) return emptyList()
        val event = definitions.events[current.eventId] ?: return listOf(
            WorkflowAction.StateChanged(GameplayWorkflowState.Error("Unknown event.")),
        )
        val plan = EventWorkflowPlanner.plan(current.eventId, event.engineRule)
        val collecting = GameplayWorkflowState.EventCollectingTargets(
            eventId = current.eventId,
            plan = plan,
            stepIndex = 0,
        )
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
            is GameplayWorkflowState.WaitingForPurchasingPlayer -> {
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

    fun onCommandSucceeded(resultContext: WorkflowCommandContext) {
        buyLocked = false
        when (resultContext) {
            is WorkflowCommandContext.ApplyEvent -> {
                if (resultContext.eventId in setOf("EVT_01", "EVT_03", "EVT_18")) {
                    // pending choice handled by view model from session
                } else {
                    reset()
                }
            }
            else -> reset()
        }
    }

    fun onCommandFailed() {
        buyLocked = false
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
        state = current
        return listOf(
            WorkflowAction.StateChanged(current),
            scanRequestForEventStep(current),
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
}
