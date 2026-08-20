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
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.banking.UndoEligibility
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.navigation.BankingScanContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
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
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)
    private val undoEligibility = UndoEligibility(definitions)

    private val _uiState = MutableStateFlow(AdvancedBankingUiState())
    val uiState: StateFlow<AdvancedBankingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AdvancedBankingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AdvancedBankingEvent> = _events.asSharedFlow()

    private var pendingPlayerId: String? = null
    private var scanPromptToken: Long = 0L

    private fun requestPlayerScan(step: AdvancedBankingStep) {
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        _uiState.update {
            it.copy(step = step, result = null, message = null)
        }
        _events.tryEmit(AdvancedBankingEvent.OpenScanner(BankingScanContext.PLAYER))
    }

    private fun requestPropertyScan(step: AdvancedBankingStep) {
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        _uiState.update { it.copy(step = step) }
        _events.tryEmit(AdvancedBankingEvent.OpenScanner(BankingScanContext.PROPERTY))
    }

    init {
        refreshUndoState()
    }

    fun onCollectGo() {
        requestPlayerScan(AdvancedBankingStep.GoScanPlayer)
    }

    fun onLocation() {
        _uiState.update {
            it.copy(step = AdvancedBankingStep.LocationIntro, result = null, message = null)
        }
    }

    fun onLocationPay() {
        requestPlayerScan(AdvancedBankingStep.LocationScanPlayer)
    }

    fun onLocationDoNothing() {
        _uiState.update { it.copy(step = AdvancedBankingStep.Hub) }
    }

    fun onJail() {
        requestPlayerScan(AdvancedBankingStep.JailScanPlayer)
    }

    fun onUndo() {
        val session = sessionManager.currentSession() ?: return
        if (!undoEligibility.canUndo(session)) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(message = "Nothing can currently be undone.")
            }
            return
        }
        _uiState.update {
            it.copy(
                step = AdvancedBankingStep.UndoConfirm,
                undoDescription = undoEligibility.undoDescription(session),
            )
        }
    }

    fun onGameStatus() {
        _events.tryEmit(AdvancedBankingEvent.NavigateToGameStatus)
    }

    fun onHistory() {
        _events.tryEmit(AdvancedBankingEvent.NavigateToHistory)
    }

    fun onBack() {
        when (_uiState.value.step) {
            AdvancedBankingStep.Hub -> _events.tryEmit(AdvancedBankingEvent.NavigateBack)
            else -> _uiState.update { it.copy(step = AdvancedBankingStep.Hub, result = null, message = null) }
        }
    }

    fun onPlayerScanned(playerId: String) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        if (definitions.players[playerId] == null) {
            _uiState.update { it.copy(message = "Unknown player card.") }
            return
        }
        when (val step = _uiState.value.step) {
            AdvancedBankingStep.GoScanPlayer -> {
                _uiState.update { it.copy(step = AdvancedBankingStep.GoConfirm(playerId)) }
            }
            AdvancedBankingStep.LocationScanPlayer -> {
                pendingPlayerId = playerId
                requestPropertyScan(AdvancedBankingStep.LocationScanProperty)
            }
            AdvancedBankingStep.JailScanPlayer -> {
                val session = sessionManager.currentSession() ?: return
                val player = session.players[playerId]
                if (player == null || !player.jailStatus) {
                    val name = playerDisplayName(playerId)
                    InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
                    _uiState.update {
                        it.copy(
                            step = AdvancedBankingStep.Hub,
                            message = "$name is not currently in Jail.",
                        )
                    }
                } else {
                    GameplayOutcomeAudio.playCue(gameAudioFeedback, GameplayAudioCue.JAIL_WORKFLOW)
                    _uiState.update { it.copy(step = AdvancedBankingStep.JailOptions(playerId)) }
                }
            }
            else -> Unit
        }
    }

    fun onPropertyScanned(propertyId: String) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        if (_uiState.value.step != AdvancedBankingStep.LocationScanProperty) return
        val playerId = pendingPlayerId ?: return
        if (definitions.properties[propertyId] == null) {
            _uiState.update { it.copy(message = "Unknown property card.") }
            return
        }
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayLocationFee(playerId, propertyId),
        ) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapLocationResult(outcome.result, playerId, propertyId, balanceBefore)
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

    fun onConfirmGo(playerId: String) {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(GameCommand.PayGoSalary(playerId)) { outcome ->
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
        executeCommand(GameCommand.PayJailFee(playerId)) { outcome ->
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

    fun onJailDoubles(playerId: String) {
        _uiState.update { it.copy(step = AdvancedBankingStep.JailDoublesConfirm(playerId)) }
    }

    fun onConfirmJailDoubles(playerId: String) {
        executeCommand(GameCommand.ReleasePlayerFromJailByDoubles(playerId)) { outcome ->
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
                    "After 3 failed turns, pay ${com.boardbanker.app.util.formatMoney(definitions.rulesConfig.jailPaymentAmount)} " +
                    "to leave Jail and use that roll to move.",
            )
        }
    }

    fun onConfirmUndo() {
        val session = sessionManager.currentSession() ?: return
        val description = undoEligibility.undoDescription(session) ?: "Last action."
        executeCommand(GameCommand.UndoLastAction) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> resultMapper.mapUndoResult(outcome.result, description)
                is BankingCommitOutcome.Rejected ->
                    resultMapper.mapUndoBlocked(
                        outcome.result.error?.let { it.toString() }
                            ?: "This action cannot be undone.",
                    ).also {
                        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                            gameAudioFeedback,
                            outcome.result.error,
                        )
                    }
                else -> resultMapper.mapUndoBlocked("This action cannot be undone.")
            }
        }
    }

    fun onDone() {
        pendingPlayerId = null
        refreshUndoState()
        _uiState.update {
            it.copy(
                step = AdvancedBankingStep.Hub,
                result = null,
                message = null,
            )
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
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
            _uiState.update {
                it.copy(
                    commandInFlight = false,
                    step = if (mapped != null) AdvancedBankingStep.Hub else it.step,
                    result = mapped,
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

    fun playerDisplayName(playerId: String): String {
        val session = sessionManager.currentSession()
        return PlayerDisplayNames.displayName(session, playerId, definitions)
    }

    fun goSalaryText(): String =
        com.boardbanker.app.util.formatMoney(definitions.rulesConfig.goSalary)

    fun locationFeeText(): String =
        com.boardbanker.app.util.formatMoney(definitions.rulesConfig.locationFee)

    fun jailFeeText(): String =
        com.boardbanker.app.util.formatMoney(definitions.rulesConfig.jailPaymentAmount)
}

class AdvancedBankingViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdvancedBankingViewModel::class.java)) {
            return AdvancedBankingViewModel(
                sessionManager,
                definitions,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
