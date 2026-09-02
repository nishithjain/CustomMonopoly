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
import com.boardbanker.app.gameplay.presentation.DiceGambleUiMapper
import com.boardbanker.app.gameplay.presentation.EventDrawUiMapper
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
import com.boardbanker.core.dice.DiceRoller
import com.boardbanker.core.dice.RandomDiceRoller
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
    private val diceRoller: DiceRoller = RandomDiceRoller(),
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
        if (!isActiveTurnPlayable()) return
        _events.tryEmit(GameEvent.NavigateToBanking)
    }

    fun onPlayerSelected(playerId: String) {
        if (_uiState.value.commandInFlight || _uiState.value.gameplayLocked) return
        _events.tryEmit(GameEvent.NavigateToPlayerDetails(playerId))
    }

    fun onScanCardRequested() {
        if (_uiState.value.commandInFlight || _uiState.value.gameplayLocked) return
        if (workflowController.hasMandatoryEventActionPending()) return
        if (sessionManager.currentSession()?.pendingDiceGamble != null) return
        if (sessionManager.currentSession()?.pendingEventDraw != null) return
        if (!isActiveTurnPlayable()) return
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
        if (_uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(message = "Finish Lucky Break before scanning another card.")
            }
            return
        }
        if (session.pendingEventDraw != null) {
            when (cardType) {
                CardType.EVENT -> handleWorkflowActions(
                    workflowController.onPendingEventDrawScanned(cardId, session),
                )
                else -> {
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update {
                        it.copy(
                            message = "EVENT CARD EXPECTED\n\n${EventDrawUiMapper.INSTRUCTION}",
                        )
                    }
                }
            }
            return
        }
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

    fun onScanLuckyDrawEventRequested() {
        if (_uiState.value.commandInFlight) return
        val session = sessionManager.currentSession() ?: return
        if (session.pendingEventDraw == null) return
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        val request = ScanRequest.event()
        transientWorkflow.enterEventIdentified()
        _uiState.update { it.withScanRequest(request) }
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun onRollLuckyBreakDice() {
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        val pending = session.pendingDiceGamble ?: run {
            commandLock.set(false)
            return
        }
        if (session.debtResolution != null) {
            commandLock.set(false)
            return
        }
        _uiState.update {
            it.copy(
                commandInFlight = true,
                diceGamble = DiceGambleUiMapper.map(session, definitions, commandInFlight = true),
            )
        }
        viewModelScope.launch {
            val diceResults = diceRoller.roll(pending.diceCount)
            val command = GameCommand.RollEventDice(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
                diceResults = diceResults,
            )
            when (val commit = sessionManager.processCommand(session, command)) {
                is ProcessCommitResult.Committed -> {
                    when (commit.result.outcome) {
                        GameOutcome.DEBT_RESOLUTION_REQUIRED -> {
                            workflowController.reset()
                            transientWorkflow.resetToReady()
                            updateFromSession(commit.session)
                            _uiState.update { it.copy(commandInFlight = false, result = null) }
                            _events.emit(GameEvent.NavigateToDebt)
                        }
                        else -> {
                            GameplayOutcomeAudio.playCommittedOutcome(
                                gameAudioFeedback,
                                commit.result,
                                session,
                                CommitAudioTrigger.GameWorkflow(
                                    WorkflowCommandContext.RollEventDice(pending.eventId),
                                ),
                            )
                            val resultUi = mapCommittedResult(
                                commit.result,
                                WorkflowCommandContext.RollEventDice(pending.eventId),
                                session,
                            )
                            if (commit.session.pendingDiceGamble == null) {
                                workflowController.reset()
                                transientWorkflow.resetToReady()
                            } else {
                                handlePendingDiceGamble(commit.session)
                            }
                            updateFromSession(commit.session)
                            _uiState.update {
                                it.copy(
                                    commandInFlight = false,
                                    result = if (commit.session.pendingDiceGamble == null) resultUi else null,
                                    workflowState = workflowController.currentState(),
                                )
                            }
                        }
                    }
                }
                is ProcessCommitResult.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        commit.result.error,
                    )
                    updateFromSession(session)
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    updateFromSession(session)
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to save the game.\nPlease try again.",
                        )
                    }
                }
                else -> {
                    updateFromSession(session)
                    _uiState.update { it.copy(commandInFlight = false) }
                }
            }
            commandLock.set(false)
        }
    }

    fun onEndTurn() {
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        val activePlayerId = session.turnState?.activePlayerId ?: run {
            commandLock.set(false)
            return
        }
        if (session.pendingDiceGamble != null) {
            commandLock.set(false)
            return
        }
        if (session.pendingEventDraw != null) {
            commandLock.set(false)
            return
        }
        _uiState.update { it.copy(commandInFlight = true) }
        viewModelScope.launch {
            val sessionBefore = session
            when (val commit = sessionManager.processCommand(session, GameCommand.EndTurn(activePlayerId))) {
                is ProcessCommitResult.Committed -> {
                    workflowController.reset()
                    transientWorkflow.resetToReady()
                    locationWorkflowHolder.clear()
                    updateFromSession(commit.session)
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            workflowState = GameplayWorkflowState.Ready,
                            result = resultMapper.mapTurnTransitionResult(commit.result, commit.session),
                            cardPresentation = null,
                            scanRequest = null,
                            scanPrompt = null,
                            expectedCardType = null,
                        )
                    }
                }
                is ProcessCommitResult.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        commit.result.error,
                    )
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to save the game.\nPlease try again.",
                        )
                    }
                }
                else -> _uiState.update { it.copy(commandInFlight = false) }
            }
            commandLock.set(false)
        }
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
                            handlePendingDiceGamble(commit.session)
                            handlePendingEventDraw(commit.session)
                            handlePendingEventExecution(commit.session, commit.result)
                            updateFromSession(commit.session)
                            _uiState.update {
                                it.copy(
                                    commandInFlight = false,
                                    result = if (
                                        commit.session.pendingDiceGamble != null ||
                                        commit.session.pendingEventDraw != null
                                    ) {
                                        null
                                    } else {
                                        resultUi
                                    },
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

    private fun handlePendingDiceGamble(session: GameSession) {
        session.pendingDiceGamble?.let { pending ->
            handleWorkflowActions(
                workflowController.enterDiceGamble(pending.eventId, pending.actingPlayerId),
            )
        }
    }

    private fun handlePendingEventDraw(session: GameSession) {
        session.pendingEventDraw?.let { pending ->
            handleWorkflowActions(
                workflowController.enterEventDrawScan(
                    parentEventId = pending.parentEventId,
                    actingPlayerId = pending.actingPlayerId,
                    chainDepth = pending.chainDepth,
                    maximumChainDepth = pending.maximumChainDepth,
                ),
            )
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
        is WorkflowCommandContext.RollEventDice ->
            resultMapper.mapDiceGambleResult(result, context.eventId)
        is WorkflowCommandContext.ResolvePendingEventDraw ->
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
        val activePlayerId = session.turnState?.activePlayerId
        val activePlayerName = activePlayerId?.let {
            PlayerDisplayNames.displayName(session, it, definitions)
        }
        _uiState.update {
            it.copy(
                loading = false,
                editionId = session.editionId,
                status = session.status,
                players = ActiveGamePresentation.buildPlayerDashboard(session, definitions),
                activePlayerId = activePlayerId,
                activePlayerName = activePlayerName,
                turnKind = session.turnState?.turnKind,
                diceGamble = DiceGambleUiMapper.map(
                    session = session,
                    definitions = definitions,
                    commandInFlight = it.commandInFlight,
                ),
                eventDraw = EventDrawUiMapper.map(
                    session = session,
                    definitions = definitions,
                    commandInFlight = it.commandInFlight,
                ),
                activeEventMessage = activeEvent,
                gameplayLocked = session.status == GameStatus.FINISHED,
            )
        }
    }

    private fun isActiveTurnPlayable(): Boolean {
        val session = sessionManager.currentSession() ?: return false
        if (session.pendingDiceGamble != null) return false
        if (session.pendingEventDraw != null) return false
        val activePlayerId = session.turnState?.activePlayerId ?: return true
        return _uiState.value.workflowState == GameplayWorkflowState.Ready &&
            _uiState.value.result == null
    }
}

class GameViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val transientWorkflow: TransientScanWorkflowHolder,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
    private val diceRoller: DiceRoller = RandomDiceRoller(),
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
                diceRoller,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
