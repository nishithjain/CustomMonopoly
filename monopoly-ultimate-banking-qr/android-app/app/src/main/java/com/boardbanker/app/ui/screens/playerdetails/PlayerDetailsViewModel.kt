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
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
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

    private var jailPassScanInProgress = false
    private var jailPassScanHandling = false

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
        if (!currentActionAvailability().collectGoEnabled) return
        _uiState.update { it.copy(step = PlayerDetailsStep.GoConfirm, result = null) }
    }

    fun onLocation() {
        if (!currentActionAvailability().locationEnabled) return
        _uiState.update { it.copy(step = PlayerDetailsStep.LocationConfirm, result = null) }
    }

    fun onGoToJail() {
        if (!currentActionAvailability().goToJailEnabled) return
        _uiState.update { it.copy(step = PlayerDetailsStep.GoToJailConfirm, result = null) }
    }

    fun onGetOutOfJail() {
        if (!currentActionAvailability().getOutOfJailEnabled) return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(result = resultMapper.mapNotInJail(playerId, session)) }
            return
        }
        _uiState.update { it.copy(step = PlayerDetailsStep.GetOutOfJailChoice, result = null) }
    }

    fun supportsJailPassScan(): Boolean = definitions.supportsJailPassScan()

    fun onScanJailPass() {
        if (!currentActionAvailability().getOutOfJailEnabled) return
        if (!supportsJailPassScan()) return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) return
        jailPassScanInProgress = true
        _events.tryEmit(
            PlayerDetailsEvent.OpenJailPassScanner(
                ScanRequest.getOutOfJailPass(definitions.jailPassEventIds()),
            ),
        )
    }

    fun onJailPassScannerCancelled() {
        jailPassScanInProgress = false
    }

    fun onJailPassScanned(cardId: String, cardType: CardType) {
        if (!jailPassScanInProgress || jailPassScanHandling || _uiState.value.commandInFlight) return
        if (!isCurrentPlayer()) {
            jailPassScanInProgress = false
            rejectInactivePlayerAction()
            return
        }
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
                        rejectedCommandMessage(outcome, session),
                    )
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                else -> null
            }
        }
    }

    fun onConfirmGo() {
        if (!isCurrentPlayer()) {
            rejectInactivePlayerAction()
            return
        }
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayGoSalary(
                playerId = playerId,
                reason = GoCollectionReason.MANUAL_BANK_ACTION,
            ),
        ) { outcome ->
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
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onConfirmLocation() {
        if (!isCurrentPlayer()) {
            rejectInactivePlayerAction()
            return
        }
        val session = sessionManager.currentSession() ?: return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayLocationFee(
                playerId = playerId,
                targetPropertyId = LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
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
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
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
        if (!isCurrentPlayer()) {
            rejectInactivePlayerAction()
            return
        }
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus == true) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(result = resultMapper.mapAlreadyInJail(playerId, session)) }
            return
        }
        executeCommand(GameCommand.SendPlayerToJail(playerId, restrictToActivePlayer = true)) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success ->
                    resultMapper.mapGoToJailResult(outcome.session, playerId)
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                else -> null
            }
        }
    }

    fun onPayJailFee() {
        if (!currentActionAvailability().getOutOfJailEnabled) return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) return
        val balanceBefore = session.players[playerId]?.balance ?: 0
        executeCommand(
            GameCommand.PayJailFee(
                playerId,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
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
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
                is BankingCommitOutcome.PersistenceFailed ->
                    resultMapper.errorResult("Unable to save the game.\nPlease try again.")
                null -> null
            }
        }
    }

    fun onUseJailPass() {
        if (!currentActionAvailability().getOutOfJailEnabled) return
        val session = sessionManager.currentSession() ?: return
        executeCommand(
            GameCommand.UseGetOutOfJailPass(
                playerId,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val session = sessionManager.currentSession()
                    resultMapper.mapJailPassResult(playerId, session ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
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

    fun onReleaseAfterDoubles() {
        if (!currentActionAvailability().getOutOfJailEnabled) return
        val session = sessionManager.currentSession() ?: return
        if (session.players[playerId]?.jailStatus != true) return
        executeCommand(
            GameCommand.ReleasePlayerFromJailByDoubles(
                playerId,
                restrictToActivePlayer = true,
            ),
        ) { outcome ->
            when (outcome) {
                is BankingCommitOutcome.Success -> {
                    val updatedSession = sessionManager.currentSession()
                    resultMapper.mapJailDoublesRelease(playerId, updatedSession ?: return@executeCommand null)
                }
                is BankingCommitOutcome.Rejected ->
                    resultMapper.errorResult(rejectedCommandMessage(outcome, session))
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
        _uiState.update { it.copy(selectedPropertyId = propertyId, selectedEnergyGridId = null) }
    }

    fun dismissPropertyPreview() {
        _uiState.update { it.copy(selectedPropertyId = null) }
    }

    fun onEnergyGridSelected(energyGridId: String) {
        _uiState.update { it.copy(selectedEnergyGridId = energyGridId, selectedPropertyId = null) }
    }

    fun dismissEnergyGridPreview() {
        _uiState.update { it.copy(selectedEnergyGridId = null) }
    }

    fun onDone() {
        _uiState.update { it.copy(step = PlayerDetailsStep.Hub, result = null) }
    }

    fun goSalaryText(): String = formatMoney(definitions.bankingValues.goSalary, definitions)

    fun locationFeeText(): String = formatMoney(definitions.bankingValues.locationFee, definitions)

    fun jailFeeText(): String = formatMoney(definitions.bankingValues.jailReleaseFee, definitions)

    private fun isCurrentPlayer(): Boolean {
        val session = sessionManager.currentSession() ?: return false
        return session.turnState?.activePlayerId == playerId
    }

    private fun rejectInactivePlayerAction() {
        val session = sessionManager.currentSession() ?: return
        InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
        _uiState.update {
            it.copy(
                step = PlayerDetailsStep.Hub,
                result = resultMapper.errorResult(inactivePlayerMessage(session)),
            )
        }
    }

    private fun inactivePlayerMessage(session: GameSession): String {
        val activePlayerId = session.turnState?.activePlayerId ?: return "This action is only available on the active player's turn."
        return resultMapper.formatCommandError(
            com.boardbanker.core.error.GameError.NotActivePlayer(
                targetPlayerId = playerId,
                activePlayerId = activePlayerId,
            ),
            session,
        )
    }

    private fun rejectedCommandMessage(
        outcome: BankingCommitOutcome.Rejected,
        session: GameSession,
    ): String = resultMapper.formatCommandError(outcome.result.error, session)

    private fun refreshFromSession(session: GameSession) {
        val player = session.players[playerId] ?: return
        val propertyCount = session.properties.values.count { state -> state.ownerPlayerId == playerId }
        val energyGridCount = ActiveGamePresentation.ownedEnergyGridCount(session, playerId)
        val activePlayerId = session.turnState?.activePlayerId
        val isCurrentPlayer = playerId == activePlayerId
        val activePlayerName = activePlayerId?.let {
            PlayerDisplayNames.displayName(session, it, definitions)
        }
        _uiState.update {
            val nextStep = if (!isCurrentPlayer && it.step != PlayerDetailsStep.Hub) {
                PlayerDetailsStep.Hub
            } else {
                it.step
            }
            it.copy(
                editionId = session.editionId,
                playerId = playerId,
                playerName = PlayerDisplayNames.displayName(session, playerId, definitions),
                tokenName = definitions.players[playerId]?.displayName.orEmpty(),
                balanceText = formatMoney(player.balance, definitions),
                playerStatusText = if (player.jailStatus) "In Jail" else "Active",
                propertyCount = propertyCount,
                energyGridCount = energyGridCount,
                totalAssetCount = propertyCount + energyGridCount,
                isActiveTurn = isCurrentPlayer,
                activePlayerName = activePlayerName,
                hasEnergyGridsInEdition = definitions.energyGrids.isNotEmpty(),
                inJail = player.jailStatus,
                jailPassCount = player.jailPassCount,
                ownedProperties = ActiveGamePresentation.buildOwnedProperties(session, playerId, definitions),
                ownedEnergyGrids = ActiveGamePresentation.buildOwnedEnergyGrids(session, playerId, definitions),
                step = nextStep,
                actionAvailability = buildActionAvailability(
                    isCurrentPlayer = isCurrentPlayer,
                    activePlayerName = activePlayerName,
                    inJail = player.jailStatus,
                    commandInFlight = it.commandInFlight,
                    step = nextStep,
                ),
            )
        }
    }

    private fun currentActionAvailability(): PlayerDetailsActionAvailability =
        _uiState.value.actionAvailability

    private fun buildActionAvailability(
        isCurrentPlayer: Boolean,
        activePlayerName: String?,
        inJail: Boolean,
        commandInFlight: Boolean,
        step: PlayerDetailsStep,
    ): PlayerDetailsActionAvailability =
        PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = isCurrentPlayer,
            activePlayerName = activePlayerName,
            inJail = inJail,
            commandInFlight = commandInFlight,
            step = step,
        )

    private fun executeCommand(
        command: GameCommand,
        mapResult: (BankingCommitOutcome?) -> com.boardbanker.app.gameplay.presentation.GameplayResultUiModel?,
    ) {
        if (_uiState.value.commandInFlight) return
        if (!isCurrentPlayer()) {
            rejectInactivePlayerAction()
            return
        }
        _uiState.update {
            it.copy(
                commandInFlight = true,
                actionAvailability = buildActionAvailability(
                    isCurrentPlayer = it.isActiveTurn,
                    activePlayerName = it.activePlayerName,
                    inJail = it.inJail,
                    commandInFlight = true,
                    step = it.step,
                ),
            )
        }
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
                    actionAvailability = buildActionAvailability(
                        isCurrentPlayer = it.isActiveTurn,
                        activePlayerName = it.activePlayerName,
                        inJail = it.inJail,
                        commandInFlight = false,
                        step = if (mapped != null && !continueLocation) PlayerDetailsStep.Hub else it.step,
                    ),
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
