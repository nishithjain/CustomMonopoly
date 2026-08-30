package com.boardbanker.app.ui.screens.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.audio.ScanPromptAudio
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.displayNameWithNumber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DebtResolutionViewModel(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)

    fun money(amount: Int): String = com.boardbanker.app.util.formatMoney(amount, definitions)

    private val _uiState = MutableStateFlow(DebtResolutionUiState())
    val uiState: StateFlow<DebtResolutionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DebtResolutionEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<DebtResolutionEvent> = _events.asSharedFlow()

    private var scanPromptToken: Long = 0L

    init {
        refreshFromSession()
    }

    fun refreshFromSession(clearSelection: Boolean = false) {
        val session = sessionManager.currentSession() ?: return
        val debt = session.debtResolution ?: return
        val debtor = session.players[debt.debtorPlayerId]!!
        val debtorName = PlayerDisplayNames.displayName(session, debt.debtorPlayerId, definitions)
        val creditorName = if (debt.creditorPlayerId == EntityRef.BANK) {
            "Bank"
        } else {
            PlayerDisplayNames.displayName(session, debt.creditorPlayerId, definitions)
        }
        val ownedProperties = session.properties.values
            .filter { it.ownerPlayerId == debt.debtorPlayerId }
            .mapNotNull { state ->
                val def = definitions.properties[state.propertyId] ?: return@mapNotNull null
                DebtPropertyOption(
                    propertyId = state.propertyId,
                    propertyName = def.displayNameWithNumber(),
                    debtValue = def.purchasePrice,
                )
            }
            .sortedBy { PropertyDisplayNames.propertyNumber(it.propertyId) ?: Int.MAX_VALUE }
        val ownedPropertyIds = ownedProperties.map { it.propertyId }.toSet()
        val preservedSelection = if (clearSelection) {
            emptySet()
        } else {
            _uiState.value.selectedPropertyIds.intersect(ownedPropertyIds)
        }
        _uiState.update {
            it.copy(
                debtorPlayerId = debt.debtorPlayerId,
                debtorName = debtorName,
                creditorPlayerId = if (debt.creditorPlayerId == EntityRef.BANK) null else debt.creditorPlayerId,
                creditorName = creditorName,
                amountDue = debt.amountRemaining + debtor.balance,
                availableCash = debtor.balance,
                remainingAfterCash = debt.amountRemaining,
                selectedPropertyIds = preservedSelection,
                properties = ownedProperties,
            ).withSettlementSummary(::money)
        }
    }

    fun onToggleProperty(propertyId: String) {
        _uiState.update { state ->
            if (state.properties.none { it.propertyId == propertyId }) {
                return@update state
            }
            val updatedSelection = if (propertyId in state.selectedPropertyIds) {
                state.selectedPropertyIds - propertyId
            } else {
                state.selectedPropertyIds + propertyId
            }
            state.copy(selectedPropertyIds = updatedSelection).withSettlementSummary(::money)
        }
    }

    fun onScanPropertyRequested() {
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        _events.tryEmit(DebtResolutionEvent.OpenPropertyScanner)
    }

    fun onPropertyScanned(propertyId: String) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        val option = _uiState.value.properties.firstOrNull { it.propertyId == propertyId }
        if (option == null) {
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update { it.copy(message = "This Property does not belong to the debtor.") }
            return
        }
        resolveDebt(listOf(propertyId))
    }

    fun onSettleSelected() {
        val selectedPropertyIds = _uiState.value.selectedPropertyIds.toList()
        if (selectedPropertyIds.isEmpty()) {
            _uiState.update { it.copy(message = "Select a Property to cover the debt.") }
            return
        }
        resolveDebt(selectedPropertyIds)
    }

    private fun resolveDebt(propertyIds: List<String>) {
        if (propertyIds.isEmpty() || _uiState.value.commandInFlight) return
        val sessionBefore = sessionManager.currentSession() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true) }
            when (val outcome = executor.execute(GameCommand.ResolveDebtWithProperties(propertyIds))) {
                is BankingCommitOutcome.Success -> {
                    if (outcome.session.debtResolution != null) {
                        refreshFromSession(clearSelection = true)
                        _uiState.update { it.copy(commandInFlight = false, result = null) }
                    } else {
                        GameplayOutcomeAudio.playCommittedOutcome(
                            gameAudioFeedback,
                            outcome.result,
                            sessionBefore,
                            CommitAudioTrigger.DebtSettled,
                        )
                        _uiState.update {
                            it.copy(
                                commandInFlight = false,
                                result = resultMapper.mapDebtSettled(
                                    result = outcome.result,
                                    propertyIds = propertyIds,
                                    sessionBefore = sessionBefore,
                                ),
                                selectedPropertyIds = emptySet(),
                            ).withSettlementSummary(::money)
                        }
                    }
                }
                is BankingCommitOutcome.DebtRequired -> {
                    refreshFromSession(clearSelection = true)
                    _uiState.update { it.copy(commandInFlight = false) }
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    gameEndAudioCoordinator.onBankruptcyCommitted(gameAudioFeedback)
                    _uiState.update { it.copy(commandInFlight = false) }
                    _events.emit(DebtResolutionEvent.NavigateToGameOver)
                }
                is BankingCommitOutcome.Rejected -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = outcome.result.error?.let { err -> err.toString() }
                                ?: "Unable to settle debt.",
                        )
                    }
                }
                is BankingCommitOutcome.PersistenceFailed -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to save the game.\nPlease try again.",
                        )
                    }
                }
                null -> _uiState.update { it.copy(commandInFlight = false) }
            }
        }
    }

    fun onCheckBankruptcy() {
        viewModelScope.launch {
            when (val outcome = executor.execute(GameCommand.CheckBankruptcy)) {
                is BankingCommitOutcome.Bankruptcy -> {
                    gameEndAudioCoordinator.onBankruptcyCommitted(gameAudioFeedback)
                    _events.emit(DebtResolutionEvent.NavigateToGameOver)
                }
                is BankingCommitOutcome.Rejected -> {
                    _uiState.update {
                        it.copy(message = outcome.result.error?.let { err -> err.toString() } ?: "Bankruptcy check failed.")
                    }
                }
                else -> Unit
            }
        }
    }

    fun onDone() {
        _events.tryEmit(DebtResolutionEvent.NavigateBack)
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

class DebtResolutionViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DebtResolutionViewModel::class.java)) {
            return DebtResolutionViewModel(
                sessionManager,
                definitions,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
