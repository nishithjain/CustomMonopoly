package com.boardbanker.app.ui.screens.playerdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayAudioCue
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.gameplay.location.LocationWorkflowConstants
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.game.ActiveGamePresentation
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
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

class PlayerDetailsViewModel(
    private val playerId: String,
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)

    private val _uiState = MutableStateFlow(PlayerDetailsUiState())
    val uiState: StateFlow<PlayerDetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlayerDetailsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayerDetailsEvent> = _events.asSharedFlow()

    init {
        sessionManager.currentSession()?.let { refreshFromSession(it) }
        viewModelScope.launch {
            sessionManager.committedSession.collect { session ->
                if (session != null) {
                    refreshFromSession(session)
                }
            }
        }
    }

    fun onBack() {
        when (_uiState.value.step) {
            PlayerDetailsStep.Hub -> _events.tryEmit(PlayerDetailsEvent.NavigateBack)
            else -> _uiState.update { it.copy(step = PlayerDetailsStep.Hub, result = null) }
        }
    }

    fun onCollectGo() {
        _uiState.update { it.copy(step = PlayerDetailsStep.GoConfirm, result = null) }
    }

    fun onLocation() {
        _uiState.update { it.copy(step = PlayerDetailsStep.LocationConfirm, result = null) }
    }

    fun onGoToJail() {
        _uiState.update { it.copy(step = PlayerDetailsStep.GoToJailConfirm, result = null) }
    }

    fun onGetOutOfJail() {
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(result = resultMapper.mapNotInJail(playerId, session)) }
            return
        }
        GameplayOutcomeAudio.playCue(gameAudioFeedback, GameplayAudioCue.JAIL_WORKFLOW)
        _uiState.update { it.copy(step = PlayerDetailsStep.JailOptions, result = null) }
    }

    fun onConfirmGo() {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(GameCommand.PayGoSalary(playerId)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapGoResult(outcome.result, playerId, balanceBefore)
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToGameOver)
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

    fun onConfirmLocation() {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(GameCommand.PayLocationFee(playerId, LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    locationWorkflowHolder.beginWaitingForDestination(playerId)
                    resultMapper.mapLocationFeeOnlyResult(outcome.result, playerId, balanceBefore)
                }
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToGameOver)
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

    fun onPropertyScanned(propertyId: String) {
        // Location destination Property scans continue on Active Game.
    }

    fun onConfirmGoToJail() {
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus == true) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(result = resultMapper.mapAlreadyInJail(playerId, session)) }
            return
        }
        executeCommand(GameCommand.SendPlayerToJail(playerId)) { outcome ->
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

    fun onPayJailFee() {
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(GameCommand.PayJailFee(playerId)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapJailFeeResult(outcome.result, playerId, balanceBefore)
                is BankingCommitOutcome.DebtRequired -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToDebt)
                    null
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    _events.tryEmit(PlayerDetailsEvent.NavigateToGameOver)
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

    fun onUseJailPass() {
        executeCommand(GameCommand.UseGetOutOfJailPass(playerId)) { outcome ->
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

    fun jailPassActionLabel(): String? {
        val count = _uiState.value.jailPassCount
        if (count <= 0) return null
        return if (count > 1) "Use Jail Pass ($count)" else "Use Jail Pass"
    }

    fun onJailDoubles() {
        _uiState.update { it.copy(step = PlayerDetailsStep.JailDoublesConfirm) }
    }

    fun onConfirmJailDoubles() {
        executeCommand(GameCommand.ReleasePlayerFromJailByDoubles(playerId)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val session = sessionManager.currentSession()
                    resultMapper.mapJailDoublesRelease(playerId, session ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(outcome.result.error?.let { it.toString() } ?: "Unable to release from Jail.")
                else -> resultMapper.errorResult("Unable to release from Jail.")
            }
        }
    }

    fun onFailedDoublesInfo() {
        _uiState.update {
            it.copy(
                result = resultMapper.errorResult(
                    "Track failed doubles physically.\n\n" +
                        "After 3 failed turns, pay ${formatMoney(definitions.bankingValues.jailReleaseFee, definitions)} " +
                        "to leave Jail and use that roll to move.",
                ),
            )
        }
    }

    fun onPropertySelected(propertyId: String) {
        _uiState.update { it.copy(selectedPropertyId = propertyId) }
    }

    fun dismissPropertyPreview() {
        _uiState.update { it.copy(selectedPropertyId = null) }
    }

    fun onDone() {
        _uiState.update { it.copy(step = PlayerDetailsStep.Hub, result = null) }
    }

    fun goSalaryText(): String = formatMoney(definitions.bankingValues.goSalary, definitions)

    fun locationFeeText(): String = formatMoney(definitions.bankingValues.locationFee, definitions)

    fun jailFeeText(): String = formatMoney(definitions.bankingValues.jailReleaseFee, definitions)

    private fun refreshFromSession(session: GameSession) {
        val player = session.players[playerId] ?: return
        _uiState.update {
            it.copy(
                editionId = session.editionId,
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, definitions),
                tokenName = definitions.players[playerId]?.displayName.orEmpty(),
                balanceText = formatMoney(player.balance, definitions),
                jailStatusText = if (player.jailStatus) "IN JAIL" else "No",
                propertyCount = session.properties.values.count { state -> state.ownerPlayerId == playerId },
                inJail = player.jailStatus,
                jailPassCount = player.jailPassCount,
                ownedProperties = ActiveGamePresentation.buildOwnedProperties(session, playerId, definitions),
            )
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
            val continueLocation = outcome is BankingCommitOutcome.Success &&
                locationWorkflowHolder.isWaitingForDestination()
            if (continueLocation) {
                _events.tryEmit(PlayerDetailsEvent.ContinueLocationOnActiveGame)
            }
            _uiState.update {
                it.copy(
                    commandInFlight = false,
                    step = if (mapped != null && !continueLocation) PlayerDetailsStep.Hub else it.step,
                    result = if (continueLocation) null else mapped,
                )
            }
        }
    }
}

class PlayerDetailsViewModelFactory(
    private val playerId: String,
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val locationWorkflowHolder: LocationWorkflowHolder,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerDetailsViewModel::class.java)) {
            return PlayerDetailsViewModel(
                playerId,
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
