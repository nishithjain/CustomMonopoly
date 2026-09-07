package com.boardbanker.app.ui.screens.banking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayAudioCue
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.audio.ScanPromptAudio
import com.boardbanker.app.gameplay.location.LocationWorkflowConstants
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.banking.UndoAuthorizationController
import com.boardbanker.app.banking.UndoAuthorizationPhase
import com.boardbanker.app.banking.UndoAuthorizationPlayer
import com.boardbanker.app.banking.UndoAuthorizationScanResult
import com.boardbanker.app.banking.UndoEligibility
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GoCollectionReason
import com.boardbanker.core.model.jailPassEventIds
import com.boardbanker.core.model.supportsJailPassScan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdvancedBankingViewModel(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)
    private val undoEligibility = UndoEligibility(definitions)
    private val undoAuthorization = UndoAuthorizationController()

    private val _uiState = MutableStateFlow(AdvancedBankingUiState())
    val uiState: StateFlow<AdvancedBankingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AdvancedBankingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AdvancedBankingEvent> = _events.asSharedFlow()

    private var scanPromptToken: Long = 0L
    private var jailPassScanInProgress = false
    private var jailPassScanHandling = false

    init {
        refreshUndoState()
        refreshHubEligibility()
    }

    fun onCollectGo() {
        if (_uiState.value.authorization.active) return
        val playerId = requireActivePlayerId() ?: return
        if (isActivePlayerJailed()) {
            notifyJailedBankActionBlocked()
            return
        }
        _uiState.update {
            it.copy(step = AdvancedBankingStep.GoConfirm(playerId), result = null, message = null)
        }
    }

    fun onLocation() {
        if (_uiState.value.authorization.active) return
        if (isActivePlayerJailed()) {
            notifyJailedBankActionBlocked()
            return
        }
        _uiState.update {
            it.copy(step = AdvancedBankingStep.LocationIntro, result = null, message = null)
        }
    }

    fun onLocationPay() {
        val playerId = requireActivePlayerId() ?: return
        if (isActivePlayerJailed()) {
            notifyJailedBankActionBlocked()
            return
        }
        _uiState.update {
            it.copy(step = AdvancedBankingStep.LocationConfirmPlayer(playerId), result = null, message = null)
        }
    }

    fun onLocationDoNothing() {
        _uiState.update { it.copy(step = AdvancedBankingStep.Hub) }
    }

    fun onGetOutOfJail() {
        if (_uiState.value.authorization.active) return
        val session = sessionManager.currentSession() ?: return
        val playerId = requireActivePlayerId() ?: return
        val player = session.players[playerId]
        if (player == null || !player.jailStatus) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(
                    step = AdvancedBankingStep.Hub,
                    result = resultMapper.mapNotInJail(playerId, session),
                )
            }
            return
        }
        GameplayOutcomeAudio.playCue(gameAudioFeedback, GameplayAudioCue.JAIL_WORKFLOW)
        _uiState.update {
            it.copy(step = AdvancedBankingStep.GetOutOfJailChoice(playerId), result = null, message = null)
        }
    }

    fun supportsJailPassScan(): Boolean = definitions.supportsJailPassScan()

    fun onScanJailPass(playerId: String) {
        if (!supportsJailPassScan()) return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) return
        jailPassScanInProgress = true
        _events.tryEmit(
            AdvancedBankingEvent.OpenScanner(
                ScanRequest.getOutOfJailPass(definitions.jailPassEventIds()),
                onCancelled = ::onJailPassScannerCancelled,
            ),
        )
    }

    fun onJailPassScannerCancelled() {
        jailPassScanInProgress = false
    }

    fun onOpenJailOptions(playerId: String) {
        _uiState.update { it.copy(step = AdvancedBankingStep.JailOptions(playerId), result = null) }
    }

    fun onGoToJail() {
        if (_uiState.value.authorization.active) return
        val session = sessionManager.currentSession() ?: return
        val playerId = requireActivePlayerId() ?: return
        if (isActivePlayerJailed()) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(
                    step = AdvancedBankingStep.Hub,
                    result = resultMapper.mapAlreadyInJail(playerId, session),
                )
            }
            return
        }
        _uiState.update {
            it.copy(step = AdvancedBankingStep.GoToJailConfirm(playerId), result = null, message = null)
        }
    }

    fun onUndo() {
        val session = sessionManager.currentSession() ?: return
        if (!undoEligibility.canUndo(session)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(message = UndoAuthorizationController.NOTHING_TO_UNDO_MESSAGE)
            }
            return
        }
        val players = session.players.entries.map { (playerId, _) ->
            UndoAuthorizationPlayer(
                playerId = playerId,
                displayName = PlayerDisplayNames.displayName(session, playerId, definitions),
                verified = false,
            )
        }
        val authorization = undoAuthorization.begin(
            players = players,
            undoDescription = undoEligibility.undoDescription(session),
        )
        _uiState.update {
            it.copy(
                step = AdvancedBankingStep.UndoAuthorization,
                result = null,
                message = null,
                authorization = authorization,
                undoDescription = authorization.undoDescription,
            )
        }
        openUndoAuthorizationScanner(playPrompt = true)
    }

    fun onCancelUndo() {
        if (_uiState.value.commandInFlight) return
        if (undoAuthorization.snapshot().phase == UndoAuthorizationPhase.COMPLETING) return
        val authorization = undoAuthorization.cancel()
        _uiState.update {
            it.copy(
                step = AdvancedBankingStep.Hub,
                authorization = authorization,
                message = null,
            )
        }
        refreshUndoState()
        refreshHubEligibility()
    }

    fun onRequestUndoScan() {
        if (!_uiState.value.authorization.active) return
        if (_uiState.value.authorization.phase != UndoAuthorizationPhase.COLLECTING) {
            return
        }
        openUndoAuthorizationScanner(playPrompt = false)
    }

    fun onGameStatus() {
        if (_uiState.value.authorization.active) return
        _events.tryEmit(AdvancedBankingEvent.NavigateToGameStatus)
    }

    fun onHistory() {
        if (_uiState.value.authorization.active) return
        _events.tryEmit(AdvancedBankingEvent.NavigateToHistory)
    }

    fun onBack() {
        when (val step = _uiState.value.step) {
            AdvancedBankingStep.Hub -> _events.tryEmit(AdvancedBankingEvent.NavigateBack)
            AdvancedBankingStep.UndoAuthorization -> onCancelUndo()
            is AdvancedBankingStep.JailDoublesConfirm -> {
                _uiState.update {
                    it.copy(step = AdvancedBankingStep.JailOptions(step.playerId), result = null, message = null)
                }
            }
            is AdvancedBankingStep.JailOptions -> {
                _uiState.update {
                    it.copy(step = AdvancedBankingStep.GetOutOfJailChoice(step.playerId), result = null, message = null)
                }
            }
            else -> _uiState.update { it.copy(step = AdvancedBankingStep.Hub, result = null, message = null) }
        }
    }

    fun onScanDelivered(cardId: String, cardType: CardType) {
        if (jailPassScanInProgress) {
            onJailPassScanned(cardId, cardType)
            return
        }
        if (_uiState.value.authorization.active) {
            onUndoAuthorizationScan(cardId, cardType)
        }
    }

    private fun onJailPassScanned(cardId: String, cardType: CardType) {
        if (!jailPassScanInProgress || jailPassScanHandling || _uiState.value.commandInFlight) return
        val playerId = requireActivePlayerId() ?: return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) {
            jailPassScanInProgress = false
            return
        }

        val allowedEventIds = definitions.jailPassEventIds()
        if (cardType != CardType.EVENT || cardId !in allowedEventIds) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            return
        }

        jailPassScanHandling = true
        jailPassScanInProgress = false
        executeCommand(
            GameCommand.GetOutOfJailWithPass(
                playerId = playerId,
                eventId = cardId,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
            jailPassScanHandling = false
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val updatedSession = sessionManager.currentSession()
                    resultMapper.mapJailPassScannedResult(playerId, updatedSession ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(
                        outcome.result.error?.let { it.toString() }
                            ?: "Unable to use Get out of Jail Pass.",
                    )
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                else -> null
            }
        }
    }

    fun onPlayerScanned(playerId: String) {
        // Bank actions no longer require player scans during the active turn.
    }

    fun onConfirmLocationPlayer(playerId: String) {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayLocationFee(
                playerId,
                LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    locationWorkflowHolder.beginWaitingForDestination(playerId)
                    resultMapper.mapLocationFeeOnlyResult(outcome.result, playerId, balanceBefore)
                }
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToGameOver)
                    null
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to pay location fee.")
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onConfirmGoToJail(playerId: String) {
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus == true) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(
                    step = AdvancedBankingStep.Hub,
                    result = resultMapper.mapAlreadyInJail(playerId, session),
                )
            }
            return
        }
        executeCommand(GameCommand.SendPlayerToJail(playerId, restrictToActivePlayer = true)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapGoToJailResult(outcome.session, playerId)
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to send to Jail.")
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                else -> null
            }
        }
    }

    fun onPropertyScanned(propertyId: String) {
        // Location destination Property scans are handled on Active Game after fee commit.
    }

    fun onConfirmGo(playerId: String) {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayGoSalary(playerId, GoCollectionReason.MANUAL_BANK_ACTION),
        ) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapGoResult(outcome.result, playerId, balanceBefore)
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToGameOver)
                    null
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to collect GO.")
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onPayJailFee(playerId: String) {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(GameCommand.PayJailFee(playerId, restrictToActivePlayer = true)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapJailFeeResult(outcome.result, playerId, balanceBefore)
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToGameOver)
                    null
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to pay jail fee.")
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onUseJailPass(playerId: String) {
        executeCommand(GameCommand.UseGetOutOfJailPass(playerId, restrictToActivePlayer = true)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val session = sessionManager.currentSession()
                    resultMapper.mapJailPassResult(playerId, session ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to use Jail pass.")
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                else -> null
            }
        }
    }

    fun jailPassActionLabel(playerId: String): String? {
        val count = sessionManager.currentSession()?.players[playerId]?.jailPassCount ?: 0
        if (count <= 0) return null
        return if (count > 1) "Use Jail Pass ($count)" else "Use Jail Pass"
    }

    fun onJailDoubles(playerId: String) {
        _uiState.update { it.copy(step = AdvancedBankingStep.JailDoublesConfirm(playerId)) }
    }

    fun onConfirmJailDoubles(playerId: String) {
        executeCommand(GameCommand.ReleasePlayerFromJailByDoubles(playerId, restrictToActivePlayer = true)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val session = sessionManager.currentSession()
                    resultMapper.mapJailDoublesRelease(playerId, session ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to release from Jail.")
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(AdvancedBankingEvent.NavigateToGameOver)
                    null
                }
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onFailedDoublesInfo() {
        _uiState.update {
            it.copy(
                message = "Track failed doubles physically.\n\n" +
                    "After 3 failed turns, pay ${com.boardbanker.app.util.formatMoney(definitions.bankingValues.jailReleaseFee, definitions)} " +
                    "to leave Jail and use that roll to move.",
            )
        }
    }

    fun onDone() {
        undoAuthorization.cancel()
        refreshUndoState()
        refreshHubEligibility()
        _uiState.update {
            it.copy(
                step = AdvancedBankingStep.Hub,
                result = null,
                message = null,
                authorization = undoAuthorization.snapshot(),
            )
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun openUndoAuthorizationScanner(playPrompt: Boolean) {
        if (playPrompt) {
            scanPromptToken = ScanPromptAudio.beginPromptSession()
            ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        }
        val remaining = undoAuthorization.snapshot().waitingPlayers.size
        _events.tryEmit(AdvancedBankingEvent.OpenScanner(ScanRequest.undoAuthorization(remaining)))
    }

    private fun onUndoAuthorizationScan(cardId: String, cardType: CardType) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        when (val result = undoAuthorization.onScan(cardType, cardId)) {
            is UndoAuthorizationScanResult.PlayerVerified -> {
                _uiState.update { it.copy(authorization = result.state, message = null) }
                if (result.readyToUndo) {
                    executeAuthorizedUndo()
                } else {
                    openUndoAuthorizationScanner(playPrompt = false)
                }
            }
            is UndoAuthorizationScanResult.AlreadyApproved -> {
                _uiState.update {
                    it.copy(
                        authorization = result.state,
                        message = UndoAuthorizationController.alreadyApprovedMessage(result.playerName),
                    )
                }
            }
            is UndoAuthorizationScanResult.WrongCard -> {
                _uiState.update {
                    it.copy(
                        authorization = result.state,
                        message = UndoAuthorizationController.WRONG_CARD_MESSAGE,
                    )
                }
            }
            is UndoAuthorizationScanResult.UnregisteredPlayer -> {
                InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                _uiState.update {
                    it.copy(
                        authorization = result.state,
                        message = UndoAuthorizationController.UNREGISTERED_PLAYER_MESSAGE,
                    )
                }
            }
            is UndoAuthorizationScanResult.Ignored -> {
                _uiState.update { it.copy(authorization = result.state) }
            }
        }
    }

    private fun executeAuthorizedUndo() {
        if (_uiState.value.commandInFlight) return
        if (undoAuthorization.snapshot().phase != UndoAuthorizationPhase.COMPLETING) return
        _uiState.update { it.copy(commandInFlight = true) }
        viewModelScope.launch {
            val sessionBefore = sessionManager.currentSession()
            val outcome = executor.execute(GameCommand.UndoLastAction)
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    if (sessionBefore != null) {
                        GameplayOutcomeAudio.playCommittedOutcome(
                            gameAudioFeedback,
                            outcome.result,
                            sessionBefore,
                            CommitAudioTrigger.Banking(GameCommand.UndoLastAction),
                        )
                    }
                    undoAuthorization.markCompleted()
                    refreshUndoState()
                    refreshHubEligibility()
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            step = AdvancedBankingStep.Hub,
                            authorization = undoAuthorization.snapshot(),
                            result = resultMapper.mapUndoResult(
                                outcome.result,
                                UndoAuthorizationController.SUCCESS_MESSAGE,
                            ),
                            message = null,
                        )
                    }
                }
                is BankingCommitOutcome.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        outcome.result.error,
                    )
                    val failed = undoAuthorization.markFailed(
                        outcome.result.error?.let { it.toString() }
                            ?: "This action cannot be undone.",
                    )
                    refreshUndoState()
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            step = AdvancedBankingStep.UndoAuthorization,
                            authorization = failed,
                            result = null,
                        )
                    }
                }
                is BankingCommitOutcome.PersistenceFailed -> {
                    val failed = undoAuthorization.markFailed("Unable to save the game.\nPlease try again.")
                    refreshUndoState()
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            step = AdvancedBankingStep.UndoAuthorization,
                            authorization = failed,
                            result = null,
                        )
                    }
                }
                else -> {
                    val failed = undoAuthorization.markFailed("This action cannot be undone.")
                    refreshUndoState()
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            step = AdvancedBankingStep.UndoAuthorization,
                            authorization = failed,
                            result = null,
                        )
                    }
                }
            }
        }
    }

    private fun executeCommand(
        command: GameCommand,
        mapResult: (BankingCommitOutcome?) -> com.boardbanker.app.gameplay.presentation.GameplayResultUiModel?,
    ) {
        if (_uiState.value.commandInFlight) return
        _uiState.update { it.copy(commandInFlight = true) }
        viewModelScope.launch {
            val sessionBefore = sessionManager.currentSession()
            val outcome = executor.execute(command)
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    if (sessionBefore != null) {
                        GameplayOutcomeAudio.playCommittedOutcome(
                            gameAudioFeedback,
                            outcome.result,
                            sessionBefore,
                            CommitAudioTrigger.Banking(command),
                        )
                    }
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    gameEndAudioCoordinator.onBankruptcyCommitted(gameAudioFeedback)
                }
                else -> Unit
            }
            val mapped = mapResult(outcome)
            refreshUndoState()
            refreshHubEligibility()
            val continueLocation = outcome is BankingCommitOutcome.Success &&
                locationWorkflowHolder.isWaitingForDestination()
            if (continueLocation) {
                _events.tryEmit(AdvancedBankingEvent.ContinueLocationOnActiveGame)
            }
            _uiState.update {
                it.copy(
                    commandInFlight = false,
                    step = when {
                        continueLocation -> AdvancedBankingStep.Hub
                        mapped != null -> AdvancedBankingStep.Hub
                        else -> it.step
                    },
                    result = if (continueLocation) null else mapped,
                )
            }
        }
    }

    private fun refreshUndoState() {
        val session = sessionManager.currentSession()
        _uiState.update {
            it.copy(
                canUndo = session?.let { s -> undoEligibility.canUndo(s) } ?: false,
                undoDescription = session?.let { s -> undoEligibility.undoDescription(s) },
            )
        }
    }

    private fun refreshHubEligibility() {
        val session = sessionManager.currentSession()
        val activePlayerId = session?.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        val inJail = activePlayerId?.let { session?.players[it]?.jailStatus } == true
        _uiState.update {
            it.copy(
                hubEligibility = BankingHubEligibility(
                    activePlayerId = activePlayerId,
                    activePlayerName = activePlayerId?.let { playerDisplayName(it) },
                    activePlayerInJail = inJail,
                    collectGoEnabled = activePlayerId != null && !inJail,
                    locationEnabled = activePlayerId != null && !inJail,
                    goToJailEnabled = activePlayerId != null && !inJail,
                    getOutOfJailEnabled = activePlayerId != null && inJail,
                ),
            )
        }
    }

    private fun requireActivePlayerId(): String? {
        val session = sessionManager.currentSession()
        val activePlayerId = session?.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        if (activePlayerId == null) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = "No active player for bank action.") }
            return null
        }
        return activePlayerId
    }

    private fun isActivePlayerJailed(): Boolean {
        val session = sessionManager.currentSession() ?: return false
        val activePlayerId = session.turnState?.activePlayerId?.takeIf { it.isNotBlank() } ?: return false
        return session.players[activePlayerId]?.jailStatus == true
    }

    private fun notifyJailedBankActionBlocked() {
        InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
        val session = sessionManager.currentSession()
        val activePlayerId = session?.turnState?.activePlayerId?.takeIf { it.isNotBlank() }
        val message = activePlayerId?.let {
            com.boardbanker.core.rules.JailGameplayGuard.blockedMessage(definitions, it)
        } ?: "This action is unavailable while in Jail."
        _uiState.update { it.copy(message = message) }
    }

    fun playerDisplayName(playerId: String): String {
        val session = sessionManager.currentSession()
        return com.boardbanker.app.player.PlayerDisplayNames.displayName(session, playerId, definitions)
    }

    fun goSalaryText(): String =
        com.boardbanker.app.util.formatMoney(definitions.bankingValues.goSalary, definitions)

    fun locationFeeText(): String =
        com.boardbanker.app.util.formatMoney(definitions.bankingValues.locationFee, definitions)

    fun jailFeeText(): String =
        com.boardbanker.app.util.formatMoney(definitions.bankingValues.jailReleaseFee, definitions)
}

class AdvancedBankingViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdvancedBankingViewModel::class.java)) {
            return AdvancedBankingViewModel(
                sessionManager,
                definitions,
                locationWorkflowHolder,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
