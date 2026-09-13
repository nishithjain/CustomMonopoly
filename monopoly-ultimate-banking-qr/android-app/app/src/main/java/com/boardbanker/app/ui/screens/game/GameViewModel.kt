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
import com.boardbanker.app.BuildConfig
import com.boardbanker.app.game.ActiveGamePresentation
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.presentation.DiceGambleUiMapper
import com.boardbanker.app.gameplay.presentation.EventDrawUiMapper
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.navigation.ActiveGameHubReturnSignal
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
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.rules.JailGameplayGuard
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
    private val activeGameHubReturnSignal: ActiveGameHubReturnSignal,
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

    private var testMode = false

    internal fun setTestUiState(state: GameUiState) {
        testMode = true
        _uiState.value = state
    }

    init {
        loadSession()
        viewModelScope.launch {
            activeGameHubReturnSignal.requests.collect {
                returnToActiveGameHub()
            }
        }
        viewModelScope.launch {
            sessionManager.committedSession.collect { session ->
                if (testMode) return@collect
                if (session == null) return@collect
                if (session.status == GameStatus.FINISHED && !_uiState.value.gameplayLocked) {
                    _events.emit(GameEvent.NavigateToGameOver)
                }
                if (session.debtResolution != null && _uiState.value.workflowState == GameplayWorkflowState.Ready) {
                    _events.emit(GameEvent.NavigateToDebt)
                }
                invalidateIncompatiblePropertyWorkflow(session)
                if (session.debtResolution == null &&
                    session.pendingEventExecution == null &&
                    session.pendingEventMultiContributorSettlement == null &&
                    workflowController.hasMandatoryEventActionPending()
                ) {
                    workflowController.reset()
                    _uiState.update { it.copy(workflowState = workflowController.currentState()) }
                }
                refreshDashboardFromSession(session)
            }
        }
    }

    private fun loadSession() {
        if (testMode) return
        viewModelScope.launch {
            if (testMode) return@launch
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
        if (!_uiState.value.actionAvailability.bankActionsEnabled) return
        val session = sessionManager.currentSession() ?: return
        if (session.turnState?.activePlayerId?.let { session.players[it]?.jailStatus } == true) {
            session.turnState?.activePlayerId?.let { _events.tryEmit(GameEvent.NavigateToPlayerDetails(it)) }
            return
        }
        if (!isActiveTurnPlayable()) return
        _events.tryEmit(GameEvent.NavigateToBanking)
    }

    fun onGetOutOfJailRequested() {
        if (!_uiState.value.actionAvailability.getOutOfJailEnabled) return
        val activePlayerId = sessionManager.currentSession()?.turnState?.activePlayerId ?: return
        _events.tryEmit(GameEvent.NavigateToPlayerDetails(activePlayerId))
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
        if (!_uiState.value.actionAvailability.scanCardEnabled) {
            activePlayerJailBlockedMessage()?.let { message ->
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update { it.copy(message = message) }
            }
            return
        }
        if (!isActiveTurnPlayable()) return
        val request = ScanRequest.gameCard()
        _uiState.update { it.withScanRequest(request) }
        transientWorkflow.resetToReady()
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun onScanPropertyRequested() {
        if (_uiState.value.commandInFlight) return
        val session = sessionManager.currentSession() ?: return
        if (activePlayerJailBlocksCardScan(session, CardType.PROPERTY)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
            invalidateIncompatiblePropertyWorkflow(session)
            return
        }
        val request = _uiState.value.scanRequest ?: ScanRequest.property()
        _events.tryEmit(GameEvent.OpenScanner(request))
    }

    fun locationFeeText(): String = formatMoney(definitions.bankingValues.locationFee, definitions)

    fun goSalaryText(): String = formatMoney(definitions.bankingValues.goSalary, definitions)

    fun money(amount: Int): String = formatMoney(amount, definitions)

    fun canAffordPurchase(purchasePrice: Int?): Boolean {
        val price = purchasePrice ?: return false
        val session = sessionManager.currentSession() ?: return false
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return false
        val balance = session.players[activePlayerId]?.balance ?: return false
        return balance >= price
    }

    fun purchaseBlockedReason(purchasePrice: Int?): String? {
        val price = purchasePrice ?: return null
        if (canAffordPurchase(price)) return null
        return "Insufficient funds for this purchase."
    }

    fun playerDisplayName(playerId: String): String =
        PlayerDisplayNames.displayName(sessionManager.currentSession(), playerId, definitions)

    fun onCardScanned(cardId: String, cardType: CardType) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        val session = sessionManager.currentSession() ?: return
        val clearScannerLaunch = _uiState.value.luckyDrawScannerLaunchInProgress
        if (clearScannerLaunch) {
            _uiState.update { state ->
                state.copy(luckyDrawScannerLaunchInProgress = false).let { updated ->
                    updated.copy(
                        eventDraw = EventDrawUiMapper.map(
                            session = session,
                            definitions = definitions,
                            commandInFlight = state.commandInFlight,
                            scannerLaunchInProgress = false,
                        ),
                    )
                }
            }
        }
        if (activePlayerJailBlocksCardScan(session, cardType)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
            invalidateIncompatiblePropertyWorkflow(session)
            return
        }
        if (_uiState.value.workflowState is GameplayWorkflowState.EventDiceGamble) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(message = "Finish Lucky Break before scanning another card.")
            }
            return
        }
        if (session.pendingEventDraw != null ||
            _uiState.value.scanRequest?.context == com.boardbanker.app.scanner.ScanContext.RESOLVE_PENDING_EVENT_DRAW
        ) {
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
                CardType.EVENT, CardType.ENERGY_GRID -> {
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
                    workflowController.onEventPropertyScanned(cardId, session)
                } else {
                    workflowController.onPropertyScanned(cardId, session)
                }
            }
            CardType.ENERGY_GRID -> workflowController.onEnergyGridScanned(cardId, session)
            CardType.EVENT -> workflowController.onEventScanned(cardId, session)
            CardType.USER -> workflowController.onUserScanned(cardId, session)
        }
        handleWorkflowActions(actions)
    }

    fun onBuyProperty() {
        if (_uiState.value.commandInFlight) return
        val session = sessionManager.currentSession() ?: return
        if (_uiState.value.workflowState is GameplayWorkflowState.UnownedEnergyGridDecision) {
            if (activePlayerJailBlocksPropertyPurchase(session)) {
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
                invalidateIncompatiblePropertyWorkflow(session)
                return
            }
            handleWorkflowActions(workflowController.onBuyEnergyGridSelected(session))
            return
        }
        if (activePlayerJailBlocksPropertyPurchase(session)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
            invalidateIncompatiblePropertyWorkflow(session)
            return
        }
        handleWorkflowActions(workflowController.onBuySelected(session))
    }

    fun onAuctionProperty() {
        val session = sessionManager.currentSession() ?: return
        if (_uiState.value.workflowState is GameplayWorkflowState.UnownedEnergyGridDecision) {
            if (activePlayerJailBlocksPropertyPurchase(session)) {
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
                invalidateIncompatiblePropertyWorkflow(session)
                return
            }
            handleWorkflowActions(workflowController.onAuctionEnergyGridSelected(session))
            return
        }
        if (activePlayerJailBlocksPropertyPurchase(session)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = activePlayerJailBlockedMessage(session)) }
            invalidateIncompatiblePropertyWorkflow(session)
            return
        }
        handleWorkflowActions(workflowController.onAuctionSelected(session))
    }

    fun onEventConfirm() {
        if (_uiState.value.commandInFlight) return
        handleWorkflowActions(workflowController.onEventConfirm())
    }

    fun onEventContinue() {
        if (_uiState.value.commandInFlight) return
        val session = sessionManager.currentSession() ?: return
        handleWorkflowActions(workflowController.onEventContinue(session))
    }

    fun onEventChoice(choice: GameCommand.EventPropertyChoiceType) {
        if (_uiState.value.commandInFlight) return
        handleWorkflowActions(workflowController.onEventChoice(choice))
    }

    fun onScanLuckyDrawEventRequested() {
        if (_uiState.value.commandInFlight || _uiState.value.luckyDrawScannerLaunchInProgress) return
        val session = sessionManager.currentSession() ?: return
        val pending = session.pendingEventDraw ?: return
        val parentEvent = definitions.events[pending.parentEventId]
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        val request = ScanRequest.resolvePendingEventDraw(
            parentEventId = pending.parentEventId,
            parentEventName = parentEvent?.name,
        )
        _uiState.update { state ->
            state.copy(luckyDrawScannerLaunchInProgress = true)
                .withScanRequest(request)
                .let { updated ->
                    updated.copy(
                        eventDraw = EventDrawUiMapper.map(
                            session = session,
                            definitions = definitions,
                            commandInFlight = state.commandInFlight,
                            scannerLaunchInProgress = true,
                        ),
                    )
                }
        }
        _events.tryEmit(GameEvent.OpenScanner(request, onCancelled = ::onLuckyDrawScannerCancelled))
    }

    fun onLuckyDrawScannerCancelled() {
        val session = sessionManager.currentSession()
        _uiState.update { state ->
            state.copy(luckyDrawScannerLaunchInProgress = false).let { updated ->
                if (session == null) {
                    updated
                } else {
                    updated.copy(
                        eventDraw = EventDrawUiMapper.map(
                            session = session,
                            definitions = definitions,
                            commandInFlight = state.commandInFlight,
                            scannerLaunchInProgress = false,
                        ),
                    )
                }
            }
        }
    }

    fun onSelectLuckyBreakInAppMode() {
        processLuckyBreakModeCommand { pending ->
            GameCommand.SelectDiceGambleMode(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
                mode = com.boardbanker.core.model.DiceGambleMode.IN_APP,
            )
        }
    }

    fun onSelectLuckyBreakPhysicalMode() {
        processLuckyBreakModeCommand { pending ->
            GameCommand.SelectDiceGambleMode(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
                mode = com.boardbanker.core.model.DiceGambleMode.PHYSICAL,
            )
        }
    }

    fun onBackFromLuckyBreakPhysical() {
        processLuckyBreakModeCommand { pending ->
            GameCommand.ResetDiceGambleMode(pending.eventId, pending.actingPlayerId)
        }
        _uiState.update { it.copy(luckyBreakPhysicalConfirm = null) }
    }

    fun onLuckyBreakPhysicalJackpot() {
        _uiState.update { it.copy(luckyBreakPhysicalConfirm = com.boardbanker.app.gameplay.presentation.PhysicalDiceConfirm.JACKPOT) }
        refreshDiceGambleUi()
    }

    fun onLuckyBreakPhysicalPenalty() {
        _uiState.update { it.copy(luckyBreakPhysicalConfirm = com.boardbanker.app.gameplay.presentation.PhysicalDiceConfirm.PENALTY) }
        refreshDiceGambleUi()
    }

    fun onCancelLuckyBreakPhysicalConfirm() {
        _uiState.update { it.copy(luckyBreakPhysicalConfirm = null) }
        refreshDiceGambleUi()
    }

    fun onConfirmLuckyBreakPhysical() {
        if (_uiState.value.luckyBreakCompletedOutcome != null) return
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        val pending = session.pendingDiceGamble ?: run {
            commandLock.set(false)
            return
        }
        val confirm = _uiState.value.luckyBreakPhysicalConfirm ?: run {
            commandLock.set(false)
            return
        }
        val outcome = when (confirm) {
            com.boardbanker.app.gameplay.presentation.PhysicalDiceConfirm.JACKPOT ->
                com.boardbanker.core.model.PhysicalDiceGambleOutcome.JACKPOT
            com.boardbanker.app.gameplay.presentation.PhysicalDiceConfirm.PENALTY ->
                com.boardbanker.core.model.PhysicalDiceGambleOutcome.PENALTY
        }
        viewModelScope.launch {
            val command = GameCommand.ResolvePhysicalDiceGamble(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
                outcome = outcome,
            )
            when (val commit = sessionManager.processCommand(session, command)) {
                is ProcessCommitResult.Committed -> {
                    when (commit.result.outcome) {
                        GameOutcome.DEBT_RESOLUTION_REQUIRED -> {
                            workflowController.reset()
                            transientWorkflow.resetToReady()
                            _uiState.update {
                                it.copy(
                                    luckyBreakPhysicalConfirm = null,
                                    luckyBreakCompletedOutcome = null,
                                    result = null,
                                )
                            }
                            updateFromSession(commit.session)
                            _events.emit(GameEvent.NavigateToDebt)
                        }
                        else -> {
                            val completed = DiceGambleUiMapper.buildCompletedOutcome(
                                session = commit.session,
                                definitions = definitions,
                                eventId = pending.eventId,
                                actingPlayerId = pending.actingPlayerId,
                                dieOne = null,
                                dieTwo = null,
                                transactions = commit.result.transactions,
                                jackpotAmount = pending.jackpotAmount,
                                penaltyAmount = pending.penaltyAmount,
                                physicalMode = true,
                            )
                            updateFromSession(commit.session)
                            _uiState.update {
                                it.copy(
                                    luckyBreakPhysicalConfirm = null,
                                    luckyBreakCompletedOutcome = completed,
                                    diceGamble = DiceGambleUiMapper.map(
                                        session = commit.session,
                                        definitions = definitions,
                                        rollInProgress = false,
                                        completedOutcome = completed,
                                    ),
                                    workflowState = workflowController.currentState(),
                                    result = null,
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
                    _uiState.update {
                        it.copy(
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                    updateFromSession(session)
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(message = "Unable to save the game.\nPlease try again.")
                    }
                    updateFromSession(session)
                }
                else -> updateFromSession(session)
            }
            commandLock.set(false)
        }
    }

    private fun processLuckyBreakModeCommand(
        buildCommand: (com.boardbanker.core.model.PendingDiceGamble) -> GameCommand,
    ) {
        if (_uiState.value.luckyBreakRollInProgress || _uiState.value.luckyBreakCompletedOutcome != null) return
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        val pending = session.pendingDiceGamble ?: run {
            commandLock.set(false)
            return
        }
        viewModelScope.launch {
            val command = buildCommand(pending)
            when (val commit = sessionManager.processCommand(session, command)) {
                is ProcessCommitResult.Committed -> {
                    handlePendingDiceGamble(commit.session)
                    completeCommandUiSync(commit.session) {
                        it.copy(
                            luckyBreakPhysicalConfirm = null,
                            result = null,
                            workflowState = workflowController.currentState(),
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
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                    updateFromSession(session)
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(message = "Unable to save the game.\nPlease try again.")
                    }
                    updateFromSession(session)
                }
                else -> updateFromSession(session)
            }
            commandLock.set(false)
        }
    }

    private fun refreshDiceGambleUi() {
        val session = sessionManager.currentSession() ?: return
        _uiState.update { state ->
            state.copy(
                diceGamble = DiceGambleUiMapper.map(
                    session = session,
                    definitions = definitions,
                    rollInProgress = state.luckyBreakRollInProgress,
                    completedOutcome = state.luckyBreakCompletedOutcome,
                    physicalConfirm = state.luckyBreakPhysicalConfirm,
                ),
            )
        }
    }

    fun onRollLuckyBreakDice() {
        if (_uiState.value.luckyBreakRollInProgress || _uiState.value.luckyBreakCompletedOutcome != null) return
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
        _uiState.update { state ->
            state.copy(
                luckyBreakRollInProgress = true,
                diceGamble = DiceGambleUiMapper.map(
                    session = session,
                    definitions = definitions,
                    rollInProgress = true,
                ),
            )
        }
        viewModelScope.launch {
            val command = GameCommand.RollEventDice(
                eventId = pending.eventId,
                actingPlayerId = pending.actingPlayerId,
            )
            when (val commit = sessionManager.processCommand(session, command)) {
                is ProcessCommitResult.Committed -> {
                    when (commit.result.outcome) {
                        GameOutcome.DEBT_RESOLUTION_REQUIRED -> {
                            workflowController.reset()
                            transientWorkflow.resetToReady()
                            _uiState.update {
                                it.copy(
                                    luckyBreakRollInProgress = false,
                                    luckyBreakCompletedOutcome = null,
                                    result = null,
                                )
                            }
                            updateFromSession(commit.session)
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
                            val dice = commit.result.rolledDice
                            if (commit.session.pendingDiceGamble == null &&
                                (dice.size >= 2 || commit.result.transactions.any {
                                    com.boardbanker.core.model.LuckyBreakEventSnapshot.isLuckyBreakResolution(it)
                                })
                            ) {
                                val completed = DiceGambleUiMapper.buildCompletedOutcome(
                                    session = commit.session,
                                    definitions = definitions,
                                    eventId = pending.eventId,
                                    actingPlayerId = pending.actingPlayerId,
                                    dieOne = dice.getOrNull(0),
                                    dieTwo = dice.getOrNull(1),
                                    transactions = commit.result.transactions,
                                    jackpotAmount = pending.jackpotAmount,
                                    penaltyAmount = pending.penaltyAmount,
                                )
                                updateFromSession(commit.session)
                                _uiState.update {
                                    it.copy(
                                        luckyBreakRollInProgress = false,
                                        luckyBreakCompletedOutcome = completed,
                                        diceGamble = DiceGambleUiMapper.map(
                                            session = commit.session,
                                            definitions = definitions,
                                            rollInProgress = false,
                                            completedOutcome = completed,
                                        ),
                                        workflowState = workflowController.currentState(),
                                        result = null,
                                    )
                                }
                            } else {
                                handlePendingDiceGamble(commit.session)
                                completeCommandUiSync(commit.session) {
                                    it.copy(
                                        luckyBreakRollInProgress = false,
                                        luckyBreakCompletedOutcome = null,
                                        result = null,
                                        workflowState = workflowController.currentState(),
                                    )
                                }
                            }
                        }
                    }
                }
                is ProcessCommitResult.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        commit.result.error,
                    )
                    _uiState.update {
                        it.copy(
                            luckyBreakRollInProgress = false,
                            message = commit.result.error?.let { resultMapper.errorResult(it).primaryMessage },
                        )
                    }
                    updateFromSession(session)
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(
                            luckyBreakRollInProgress = false,
                            message = "Unable to save the game.\nPlease try again.",
                        )
                    }
                    updateFromSession(session)
                }
                else -> {
                    _uiState.update { it.copy(luckyBreakRollInProgress = false) }
                    updateFromSession(session)
                }
            }
            commandLock.set(false)
        }
    }

    fun onLuckyBreakContinue() {
        workflowController.reset()
        transientWorkflow.resetToReady()
        _uiState.update {
            it.copy(
                luckyBreakCompletedOutcome = null,
                luckyBreakRollInProgress = false,
                luckyBreakPhysicalConfirm = null,
                workflowState = GameplayWorkflowState.Ready,
                result = null,
                diceGamble = null,
            )
        }
        sessionManager.currentSession()?.let { updateFromSession(it) }
    }

    fun onEndTurn() {
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        if (!_uiState.value.actionAvailability.endTurnEnabled) {
            activePlayerJailBlockedMessage(session)?.let { message ->
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update { it.copy(message = message) }
            }
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
                    GameplayOutcomeAudio.playCommittedOutcome(
                        gameAudioFeedback,
                        commit.result,
                        sessionBefore,
                        CommitAudioTrigger.Banking(GameCommand.EndTurn(activePlayerId)),
                    )
                    completeCommandUiSync(commit.session) {
                        it.copy(
                            workflowState = GameplayWorkflowState.Ready,
                            result = null,
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
        val preview = when (val current = _uiState.value.workflowState) {
            is GameplayWorkflowState.EventIntro -> current.eventId to current.pendingEventParentId
            is GameplayWorkflowState.EventCollectingTargets -> current.eventId to null
            is GameplayWorkflowState.EventConfirm -> current.eventId to null
            else -> null
        }
        if (preview != null) {
            cancelEventPreview(preview.first, preview.second)
            return
        }
        if (_uiState.value.workflowState is GameplayWorkflowState.LocationWaitingForDestinationProperty) {
            locationWorkflowHolder.clear()
        }
        handleWorkflowActions(workflowController.onCancel())
        transientWorkflow.resetToReady()
    }

    private fun cancelEventPreview(eventId: String, pendingEventParentId: String?) {
        if (!commandLock.compareAndSet(false, true)) return
        val session = sessionManager.currentSession() ?: run {
            commandLock.set(false)
            return
        }
        val actingPlayerId = session.pendingEventDraw?.actingPlayerId
            ?: session.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
            ?: run {
                commandLock.set(false)
                return
            }
        _uiState.update { it.copy(commandInFlight = true) }
        viewModelScope.launch {
            when (val commit = sessionManager.processCommand(
                session,
                GameCommand.CancelEventPreview(eventId, actingPlayerId),
            )) {
                is ProcessCommitResult.Committed -> {
                    workflowController.reset()
                    transientWorkflow.resetToReady()
                    locationWorkflowHolder.clear()
                    updateFromSession(commit.session)
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            result = null,
                            cardPresentation = null,
                            message = null,
                        ).withScanRequest(null)
                    }
                    if (pendingEventParentId != null) {
                        handleWorkflowActions(
                            workflowController.enterEventDrawScan(
                                parentEventId = pendingEventParentId,
                                actingPlayerId = actingPlayerId,
                            ),
                        )
                        onScanLuckyDrawEventRequested()
                    }
                }
                is ProcessCommitResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = commit.result.error?.let { error -> resultMapper.errorResult(error).primaryMessage },
                        )
                    }
                }
                is ProcessCommitResult.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to save the cancelled Event.\nPlease try again.",
                        )
                    }
                }
            }
            commandLock.set(false)
        }
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

    fun returnToActiveGameHub() {
        workflowController.reset()
        transientWorkflow.resetToReady()
        locationWorkflowHolder.clear()
        sessionManager.currentSession()?.let { updateFromSession(it) }
        _uiState.update {
            it.copy(
                workflowState = GameplayWorkflowState.Ready,
                result = null,
                scanRequest = null,
                scanPrompt = null,
                expectedCardType = null,
                message = null,
                cardPresentation = null,
            )
        }
    }

    fun requestEndGame() {
        if (!ActiveGameCardUiPolicy.showGameTerminationActions(
                workflowState = _uiState.value.workflowState,
                result = _uiState.value.result,
                gameplayLocked = _uiState.value.gameplayLocked,
            )
        ) {
            return
        }
        _uiState.update { it.copy(showEndGameConfirm = true) }
    }

    fun dismissEndGameConfirm() {
        _uiState.update { it.copy(showEndGameConfirm = false) }
    }

    fun confirmEndGame() {
        if (!commandLock.compareAndSet(false, true)) return
        viewModelScope.launch {
            val session = sessionManager.currentSession()
            if (session == null) {
                commandLock.set(false)
                return@launch
            }
            when (val commit = sessionManager.processCommand(session, GameCommand.ConcludeGame)) {
                is ProcessCommitResult.Committed -> {
                    workflowController.reset()
                    transientWorkflow.resetToReady()
                    locationWorkflowHolder.clear()
                    updateFromSession(commit.session)
                    _uiState.update {
                        it.copy(
                            showEndGameConfirm = false,
                            commandInFlight = false,
                            gameplayLocked = true,
                            status = commit.session.status,
                        )
                    }
                    gameEndAudioCoordinator.onGameConcludedForWinnerPresentation()
                    _events.emit(GameEvent.NavigateToGameOver)
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            showEndGameConfirm = false,
                            message = "Unable to end the game. Please try again.",
                        )
                    }
                }
            }
            commandLock.set(false)
        }
    }

    fun requestAbandonGame() {
        if (!ActiveGameCardUiPolicy.showGameTerminationActions(
                workflowState = _uiState.value.workflowState,
                result = _uiState.value.result,
                gameplayLocked = _uiState.value.gameplayLocked,
            )
        ) {
            return
        }
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
                    locationWorkflowHolder.clear()
                    sessionManager.currentSession()?.let { updateFromSession(it) }
                    _uiState.update {
                        it.copy(
                            workflowState = GameplayWorkflowState.Ready,
                            result = null,
                            scanRequest = null,
                            scanPrompt = null,
                            expectedCardType = null,
                            message = null,
                            cardPresentation = null,
                        )
                    }
                    _events.tryEmit(
                        GameEvent.NavigateToAuction(
                            propertyId = action.propertyId,
                            energyGridId = action.energyGridId,
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
            CardType.ENERGY_GRID -> transientWorkflow.enterWaitingForProperty()
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
            is GameplayWorkflowState.WaitingForRentPayerEnergyGrid -> {
                _uiState.update { it.withScanRequest(ScanRequest.player()) }
            }
            is GameplayWorkflowState.WaitingForExpectedEnergyGridScan -> {
                val gridName = com.boardbanker.core.model.EnergyGridDisplayNames.displayNameWithNumber(
                    state.energyGridId,
                    definitions,
                )
                _uiState.update { it.withScanRequest(ScanRequest.energyGrid(state.energyGridId, gridName)) }
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
        sessionManager.currentSession()?.let { refreshDashboardFromSession(it) }
    }

    private fun shouldSkipPendingEventResume(session: GameSession): Boolean {
        val pending = session.pendingEventExecution ?: return true
        val resolution = session.pendingEventResolution
        if (resolution?.isBankingRecordedFor(pending.eventId, pending.actingPlayerId) == true) return true
        if (session.transactions.any {
                it.transactionType == TransactionType.EVENT_APPLIED &&
                    it.eventId == pending.eventId &&
                    it.playerId == pending.actingPlayerId
            }
        ) {
            return true
        }
        return session.transactions.any {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER &&
                it.eventId == pending.eventId &&
                it.playerId == pending.actingPlayerId
        }
    }

    private fun handlePendingEventExecution(session: GameSession, result: GameResult) {
        if (session.pendingEventChoice != null) return
        if (session.pendingEventExecution != null && session.debtResolution == null) {
            if (shouldSkipPendingEventResume(session)) {
                workflowController.reset()
                _uiState.update { it.copy(workflowState = workflowController.currentState()) }
            } else {
                handleWorkflowActions(workflowController.resumePendingEventExecution(session))
            }
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
        refreshDashboardFromSession(session)
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
                            handlePendingEnergyGridLanding(commit.session)
                            completeCommandUiSync(commit.session) {
                                it.copy(
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
                    completeCommandUiSync(session) {
                        it.copy(
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

    private fun handlePendingEnergyGridLanding(session: GameSession) {
        if (session.pendingEnergyGridLanding == null) return
        handleWorkflowActions(workflowController.beginPendingEnergyGridLanding(session))
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
        is WorkflowCommandContext.EnergyGridPurchase ->
            resultMapper.mapEnergyGridPurchaseResult(result, context.playerId, context.energyGridId, context.balanceBefore)
        is WorkflowCommandContext.EnergyGridLanding ->
            resultMapper.mapEnergyGridLandingResult(result, context.playerId, context.energyGridId, sessionBefore)
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

    private fun completeCommandUiSync(session: GameSession, transform: (GameUiState) -> GameUiState) {
        _uiState.update { transform(it.copy(commandInFlight = false)) }
        refreshDashboardFromSession(session)
    }

    private fun invalidateIncompatiblePropertyWorkflow(session: GameSession) {
        if (!workflowController.isIncompatiblePropertyWorkflowForJailedPlayer(
                _uiState.value.workflowState,
                session,
            )
        ) {
            return
        }
        workflowController.reset()
        _uiState.update {
            it.copy(
                workflowState = GameplayWorkflowState.Ready,
                cardPresentation = null,
                scanRequest = null,
                scanPrompt = null,
                expectedCardType = null,
            )
        }
    }

    private fun refreshDashboardFromSession(session: GameSession) {
        invalidateIncompatiblePropertyWorkflow(session)
        val commandInFlight = _uiState.value.commandInFlight
        val workflowState = _uiState.value.workflowState
        val activePlayerId = session.turnState?.activePlayerId
        val activePlayerName = activePlayerId?.let {
            PlayerDisplayNames.displayName(session, it, definitions)
        }
        val activePlayerInJail = activePlayerId?.let { session.players[it]?.jailStatus } == true
        val jailResolutionMessage = if (activePlayerInJail) {
            workflowController.jailResolutionGuidance(session)
        } else {
            null
        }
        val actionAvailability = ActiveGameActionAvailability.forActivePlayer(
            activePlayerInJail = activePlayerInJail,
            commandInFlight = commandInFlight,
            gameplayLocked = session.status == GameStatus.FINISHED,
            workflowState = workflowState,
            hasMandatoryEventPending = workflowController.hasMandatoryEventActionPending(),
            hasPendingDiceGamble = session.pendingDiceGamble != null,
            hasPendingEventDraw = session.pendingEventDraw != null,
        )
        val endTurnSubtitle = ActiveGameEndTurnPresentation.subtitle(activePlayerName)
        val endTurnDisabledReason = ActiveGameEndTurnPresentation.disabledReason(
            activePlayerName = activePlayerName,
            actionAvailability = actionAvailability,
            workflowState = workflowState,
            hasPendingDiceGamble = session.pendingDiceGamble != null,
            hasPendingEventDraw = session.pendingEventDraw != null,
            commandInFlight = commandInFlight,
            gameplayLocked = session.status == GameStatus.FINISHED,
            luckyDrawEventName = session.pendingEventDraw?.let { pending ->
                definitions.events[pending.parentEventId]?.name ?: "Lucky Draw"
            } ?: "Lucky Draw",
            luckyBreakEventName = session.pendingDiceGamble?.let { pending ->
                definitions.events[pending.eventId]?.name ?: "Lucky Break"
            } ?: "Lucky Break",
        )
        _uiState.update {
            it.copy(
                loading = false,
                editionId = session.editionId,
                debugPreset = BuildConfig.DEBUG && session.debugPresetId != null,
                status = session.status,
                players = ActiveGamePresentation.buildPlayerDashboard(session, definitions),
                activePlayerId = activePlayerId,
                activePlayerName = activePlayerName,
                activePlayerInJail = activePlayerInJail,
                jailResolutionMessage = jailResolutionMessage,
                actionAvailability = actionAvailability,
                endTurnSubtitle = endTurnSubtitle,
                endTurnDisabledReason = endTurnDisabledReason,
                endTurnContentDescription = ActiveGameEndTurnPresentation.contentDescription(activePlayerName),
                turnKind = session.turnState?.turnKind,
                diceGamble = when {
                    it.luckyBreakCompletedOutcome != null -> DiceGambleUiMapper.map(
                        session = session,
                        definitions = definitions,
                        rollInProgress = false,
                        completedOutcome = it.luckyBreakCompletedOutcome,
                    )
                    session.pendingDiceGamble != null ||
                        workflowState is GameplayWorkflowState.EventDiceGamble -> DiceGambleUiMapper.map(
                        session = session,
                        definitions = definitions,
                        rollInProgress = it.luckyBreakRollInProgress,
                        physicalConfirm = it.luckyBreakPhysicalConfirm,
                    )
                    else -> null
                },
                eventDraw = EventDrawUiMapper.map(
                    session = session,
                    definitions = definitions,
                    commandInFlight = it.commandInFlight,
                    scannerLaunchInProgress = it.luckyDrawScannerLaunchInProgress,
                ),
                gameplayLocked = session.status == GameStatus.FINISHED,
            )
        }
    }

    private fun isActiveTurnPlayable(): Boolean {
        val session = sessionManager.currentSession() ?: return false
        if (session.pendingDiceGamble != null) return false
        if (session.pendingEventDraw != null) return false
        if (session.turnState?.activePlayerId?.let { session.players[it]?.jailStatus } == true) {
            return false
        }
        return _uiState.value.workflowState == GameplayWorkflowState.Ready &&
            _uiState.value.result == null
    }

    private fun activePlayerJailBlockedMessage(session: GameSession? = sessionManager.currentSession()): String? {
        session ?: return null
        return JailGameplayGuard.activePlayerJailGuidance(definitions, session)
    }

    private fun activePlayerJailBlocksCardScan(session: GameSession, cardType: CardType): Boolean {
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return false
        if (session.players[activePlayerId]?.jailStatus != true) return false
        return when (cardType) {
            CardType.PROPERTY,
            CardType.EVENT,
            CardType.ENERGY_GRID,
            -> true
            CardType.USER -> false
            else -> false
        }
    }

    private fun activePlayerJailBlocksPropertyPurchase(session: GameSession): Boolean {
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return false
        return JailGameplayGuard.propertyPurchaseBlockedMessage(definitions, session, activePlayerId) != null
    }
}

class GameViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val transientWorkflow: TransientScanWorkflowHolder,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val activeGameHubReturnSignal: ActiveGameHubReturnSignal,
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
                activeGameHubReturnSignal,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
