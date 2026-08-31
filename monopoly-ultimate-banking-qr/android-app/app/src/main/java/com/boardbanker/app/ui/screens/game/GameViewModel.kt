package com.boardbanker.app.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.ScanPromptAudio
import com.boardbanker.app.game.ActiveGamePresentation
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.gameplay.location.LocationWorkflowConstants
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowController
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.gameplay.workflow.WorkflowAction
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.app.gameplay.workflow.WorkflowScanRequest
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.persistence.SavedGameLoadResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.boardbanker.app.util.formatMoney
import java.util.concurrent.atomic.AtomicBoolean

class GameViewModel(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val transientWorkflow: TransientScanWorkflowHolder,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val workflowController = GameplayWorkflowController(definitions)
    private val resultMapper = GameplayResultMapper(definitions)
    private val commandLock = AtomicBoolean(false)
    private var scanPromptToken: Long = 0L

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    init {
        loadSession()
        viewModelScope.launch {
            sessionManager.committedSession.collect { session ->
                if (session == null) return@collect
                if (session.status == GameStatus.FINISHED && !_uiState.value.gameplayLocked) {
                    _events.emit(GameEvent.NavigateToGameOver)
                }
                if (session.debtResolution != null && _uiState.value.workflowState == GameplayWorkflowState.Ready) {
                    _events.emit(GameEvent.NavigateToDebt)
                }
                refreshDashboardFromSession(session)
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            val session = sessionManager.currentSession()
                ?: when (val load = sessionManager.restoreFromStorage()) {
                    is SavedGameLoadResult.Success -> load.session
                    else -> null
                }
            if (session == null) {
                _uiState.update { it.copy(loading = false, message = "No active game found.") }
            } else if (session.status == GameStatus.FINISHED) {
                _uiState.update { it.copy(loading = false, gameplayLocked = true, status = session.status) }
                _events.emit(GameEvent.NavigateToGameOver)
            } else if (session.debtResolution != null) {
                transientWorkflow.resetToReady()
                updateFromSession(session)
                _events.emit(GameEvent.NavigateToDebt)
            } else if (session.status != GameStatus.ACTIVE) {
                _uiState.update { it.copy(loading = false, message = "No active game found.") }
            } else {
                transientWorkflow.resetToReady()
                updateFromSession(session)
                handleWorkflowActions(workflowController.restoreWorkflowFromSession(session))
                resumeLocationWorkflowIfPending()
            }
        }
    }

    fun resumeLocationWorkflowIfPending() {
        val playerId = locationWorkflowHolder.landingPlayerId ?: return
        if (_uiState.value.workflowState is GameplayWorkflowState.LocationWaitingForDestinationProperty) {
            return
        }
        handleWorkflowActions(workflowController.enterLocationWaitingForDestination(playerId))
    }

    fun onScanRequested() {
        if (_uiState.value.commandInFlight) return
        val request = _uiState.value.scanRequest ?: return
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun onBankActionsRequested() {
        if (_uiState.value.commandInFlight || _uiState.value.gameplayLocked) return
        if (workflowController.hasMandatoryEventActionPending()) return
        _events.tryEmit(GameEvent.NavigateToBanking)
    }

    fun onPlayerSelected(playerId: String) {
        if (_uiState.value.commandInFlight || _uiState.value.gameplayLocked) return
        _events.tryEmit(GameEvent.NavigateToPlayerDetails(playerId))
    }

    fun onScanCardRequested() {
        if (_uiState.value.commandInFlight || _uiState.value.gameplayLocked) return
        if (workflowController.hasMandatoryEventActionPending()) return
        val request = ScanRequest.gameCard()
        _uiState.update { it.withScanRequest(request) }
        transientWorkflow.resetToReady()
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun onScanPropertyRequested() {
        if (_uiState.value.commandInFlight) return
        val request = _uiState.value.scanRequest ?: ScanRequest.property()
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun locationFeeText(): String = formatMoney(definitions.bankingValues.locationFee, definitions)

    fun goSalaryText(): String = formatMoney(definitions.bankingValues.goSalary, definitions)

    fun money(amount: Int): String = formatMoney(amount, definitions)

    fun playerDisplayName(playerId: String): String =
        PlayerDisplayNames.displayName(sessionManager.currentSession(), playerId, definitions)

    fun onCardScanned(cardId: String, cardType: CardType) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        val session = sessionManager.currentSession() ?: return
        val workflowState = _uiState.value.workflowState
        if (workflowState is GameplayWorkflowState.LocationWaitingForDestinationProperty) {
            when (cardType) {
                CardType.PROPERTY -> {
                    locationWorkflowHolder.clear()
                    handleWorkflowActions(
                        workflowController.beginLocationDestinationProperty(
                            playerId = workflowState.playerId,
                            propertyId = cardId,
                            session = session,
                        ),
                    )
                }
                CardType.USER -> {
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update {
                        it.copy(message = "PROPERTY CARD EXPECTED\n\nPlease scan the destination Property card.")
                    }
                }
                CardType.EVENT -> {
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update {
                        it.copy(message = "PROPERTY CARD EXPECTED\n\nPlease scan the destination Property card.")
                    }
                }
            }
            return
        }
        val actions = when (cardType) {
            CardType.PROPERTY -> {
                if (_uiState.value.workflowState is GameplayWorkflowState.EventCollectingTargets) {
                    workflowController.onEventPropertyScanned(cardId)
                } else {
                    workflowController.onPropertyScanned(cardId, session)
                }
            }
            CardType.EVENT -> workflowController.onEventScanned(cardId)
            CardType.USER -> workflowController.onUserScanned(cardId, session)
        }
        handleWorkflowActions(actions)
    }

    fun onBuyProperty() {
        if (_uiState.value.commandInFlight) return
        val session = sessionManager.currentSession() ?: return
        handleWorkflowActions(workflowController.onBuySelected(session))
    }

    fun onAuctionProperty() {
        val session = sessionManager.currentSession() ?: return
        handleWorkflowActions(workflowController.onAuctionSelected(session))
    }

    fun onEventConfirm() {
        if (_uiState.value.commandInFlight) return
        handleWorkflowActions(workflowController.onEventConfirm())
    }

    fun onEventContinue() {
        if (_uiState.value.commandInFlight) return
        handleWorkflowActions(workflowController.onEventContinue())
    }

    fun onEventChoice(choice: GameCommand.EventPropertyChoiceType) {
        if (_uiState.value.commandInFlight) return
        handleWorkflowActions(workflowController.onEventChoice(choice))
    }

    fun onCancelWorkflow() {
        if (_uiState.value.workflowState is GameplayWorkflowState.LocationWaitingForDestinationProperty) {
            locationWorkflowHolder.clear()
        }
        handleWorkflowActions(workflowController.onCancel())
        transientWorkflow.resetToReady()
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onDone() {
        handleWorkflowActions(workflowController.onDone())
        transientWorkflow.resetToReady()
        locationWorkflowHolder.clear()
        sessionManager.currentSession()?.let { updateFromSession(it) }
        _uiState.update {
            it.copy(
                result = null,
                scanRequest = null,
                scanPrompt = null,
                expectedCardType = null,
                message = null,
                cardPresentation = null,
            )
        }
    }

    fun requestAbandonGame() {
        _uiState.update { it.copy(showAbandonConfirm = true) }
    }

    fun dismissAbandonConfirm() {
        _uiState.update { it.copy(showAbandonConfirm = false) }
    }

    fun confirmAbandonGame() {
        viewModelScope.launch {
            sessionManager.deleteCurrentGame()
            workflowController.reset()
            transientWorkflow.resetToReady()
            locationWorkflowHolder.clear()
            _uiState.update { it.copy(showAbandonConfirm = false) }
            _events.emit(GameEvent.NavigateHome)
        }
    }

    private fun handleWorkflowActions(actions: List<WorkflowAction>) {
        actions.forEach { action ->
            when (action) {
                is WorkflowAction.StateChanged -> applyWorkflowState(action.state)
                is WorkflowAction.RequestScan -> openScanner(action.request)
                is WorkflowAction.ExecuteCommand -> executeCommand(action.request)
                WorkflowAction.Cancelled -> {
                    applyWorkflowState(GameplayWorkflowState.Ready)
                    _uiState.update { it.copy(cardPresentation = null).withScanRequest(null) }
                }
                is WorkflowAction.NavigateToAuction -> {
                    workflowController.reset()
                    transientWorkflow.resetToReady()
                    _events.tryEmit(
                        GameEvent.NavigateToAuction(
                            propertyId = action.propertyId,
                            startedByPlayerId = action.startedByPlayerId,
                        ),
                    )
                }
                is WorkflowAction.WrongCardType -> {
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update { it.copy(message = action.message) }
                }
            }
        }
    }

    private fun openScanner(request: WorkflowScanRequest) {
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        when (request.scanRequest.singleExpectedType) {
            CardType.USER -> transientWorkflow.enterWaitingForPlayer()
            CardType.PROPERTY -> {
                if (_uiState.value.workflowState is GameplayWorkflowState.LocationWaitingForDestinationProperty) {
                    transientWorkflow.enterLocationWaitingForDestination()
                } else {
                    transientWorkflow.enterWaitingForProperty()
                }
            }
            CardType.EVENT -> transientWorkflow.enterEventIdentified()
            else -> transientWorkflow.resetToReady()
        }
        _uiState.update { it.withScanRequest(request.scanRequest) }
        _events.tryEmit(GameEvent.OpenScanner(request.scanRequest))
    }

    private fun applyWorkflowState(state: GameplayWorkflowState) {
        val session = sessionManager.currentSession()
        _uiState.update {
            it.copy(
                workflowState = state,
                message = null,
                cardPresentation = ActiveGameCardPresentationBuilder.build(state, definitions, session),
            )
        }
        when (state) {
            is GameplayWorkflowState.PlayerInfo -> {
                val currentSession = session ?: return
                _uiState.update {
                    it.copy(result = resultMapper.mapPlayerInfo(state.playerId, currentSession))
                }
            }
            is GameplayWorkflowState.WaitingForRentPayer -> {
                _uiState.update { it.withScanRequest(ScanRequest.player()) }
            }
            is GameplayWorkflowState.EventIntro -> {
                _uiState.update { it.withScanRequest(null) }
            }
            is GameplayWorkflowState.Error -> {
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update { it.copy(message = state.message) }
            }
            is GameplayWorkflowState.LocationWaitingForDestinationProperty -> {
                scanPromptToken = ScanPromptAudio.beginPromptSession()
                ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
                transientWorkflow.enterLocationWaitingForDestination()
                _uiState.update { it.withScanRequest(ScanRequest.property()) }
            }
            is GameplayWorkflowState.EventCollectingTargets -> {
                val step = state.plan.steps.getOrNull(state.stepIndex)
                val overlay = com.boardbanker.app.gameplay.workflow.EventWorkflowPlanner.scanPrompt(step)
                _uiState.update { it.copy(scanPrompt = overlay) }
            }
            else -> Unit
        }
    }

    private fun handlePendingEventExecution(session: GameSession, result: GameResult) {
        if (session.pendingEventChoice != null) return
        if (session.pendingEventExecution != null) {
            handleWorkflowActions(workflowController.resumePendingEventExecution(session))
        } else if (result.outcome != GameOutcome.PENDING_ACTION) {
            workflowController.reset()
            _uiState.update { it.copy(workflowState = workflowController.currentState()) }
        }
    }

    private fun executeCommand(request: com.boardbanker.app.gameplay.workflow.WorkflowCommandRequest) {
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        _uiState.update { it.copy(commandInFlight = true) }
        viewModelScope.launch {
            val sessionBefore = session
            when (val commit = sessionManager.processCommand(session, request.command)) {
                is ProcessCommitResult.Committed -> {
                    when (commit.result.outcome) {
                        GameOutcome.DEBT_RESOLUTION_REQUIRED -> {
                            workflowController.onCommandFailed()
                            transientWorkflow.resetToReady()
                            updateFromSession(commit.session)
                            _uiState.update { it.copy(commandInFlight = false) }
                            _events.emit(GameEvent.NavigateToDebt)
                        }
                        GameOutcome.BANKRUPTCY -> {
                            gameEndAudioCoordinator.onBankruptcyCommitted(gameAudioFeedback)
                            workflowController.reset()
                            transientWorkflow.resetToReady()
                            updateFromSession(commit.session)
                            _uiState.update {
                                it.copy(commandInFlight = false, gameplayLocked = true, status = commit.session.status)
                            }
                            _events.emit(GameEvent.NavigateToGameOver)
                        }
                        else -> {
                            GameplayOutcomeAudio.playCommittedOutcome(
                                gameAudioFeedback,
                                commit.result,
                                sessionBefore,
                                CommitAudioTrigger.GameWorkflow(request.context),
                            )
                            val resultUi = mapCommittedResult(commit.result, request.context, sessionBefore)
                            workflowController.onCommandSucceeded(request.context, commit.session)
                            transientWorkflow.resetToReady()
                            handlePendingEventChoice(commit.result)
                            handlePendingEventExecution(commit.session, commit.result)
                            updateFromSession(commit.session)
                            _uiState.update {
                                it.copy(
                                    commandInFlight = false,
                                    result = resultUi,
                                    workflowState = workflowController.currentState(),
                                    cardPresentation = null,
                                )
                            }
                        }
                    }
                }
                is ProcessCommitResult.Rejected -> {
                    workflowController.onCommandFailed()
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        commit.result.error,
                    )
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            result = resultMapper.errorResult(commit.result.error),
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    workflowController.onCommandFailed()
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to save the game.\nPlease try again.",
                        )
                    }
                }
            }
            commandLock.set(false)
        }
    }

    private fun handlePendingEventChoice(result: GameResult) {
        val pending = result.session.pendingEventChoice ?: return
        if (result.outcome != GameOutcome.PENDING_ACTION) return
        workflowController.beginEventPropertyChoice(
            eventId = pending.eventId,
            actingPlayerId = pending.actingPlayerId,
            propertyId = pending.propertyId,
        )
        _uiState.update {
            it.copy(workflowState = workflowController.currentState())
        }
    }

    private fun mapCommittedResult(
        result: GameResult,
        context: WorkflowCommandContext,
        sessionBefore: GameSession,
    ): GameplayResultUiModel = when (context) {
        is WorkflowCommandContext.Purchase ->
            resultMapper.mapPurchaseResult(result, context.playerId, context.propertyId, context.balanceBefore)
        is WorkflowCommandContext.PropertyLanding ->
            resultMapper.mapPropertyLandingResult(result, context.playerId, context.propertyId, sessionBefore)
        is WorkflowCommandContext.ApplyEvent ->
            resultMapper.mapEventResult(result, context.eventId)
        is WorkflowCommandContext.EventChoice ->
            resultMapper.mapEventResult(result, context.eventId)
    }

    private fun updateFromSession(session: GameSession) {
        refreshDashboardFromSession(session)
    }

    private fun refreshDashboardFromSession(session: GameSession) {
        val activeEvent = session.temporaryEffects.firstOrNull {
            it.active && it.effectType == "FORCE_LEVEL_1_RENT"
        }?.let { effect ->
            "On The Run\n${effect.remainingUses} rent payment(s) remaining"
        }
        _uiState.update {
            it.copy(
                loading = false,
                editionId = session.editionId,
                status = session.status,
                players = ActiveGamePresentation.buildPlayerDashboard(session, definitions),
                activeEventMessage = activeEvent,
                gameplayLocked = session.status == GameStatus.FINISHED,
            )
        }
    }
}

class GameViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val transientWorkflow: TransientScanWorkflowHolder,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(
                sessionManager,
                definitions,
                transientWorkflow,
                locationWorkflowHolder,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
