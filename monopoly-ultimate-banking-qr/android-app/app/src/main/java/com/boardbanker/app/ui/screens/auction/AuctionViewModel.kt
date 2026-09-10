package com.boardbanker.app.ui.screens.auction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.audio.CommitAudioTrigger
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.GameplayOutcomeAudio
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.audio.ScanPromptAudio
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.EnergyGridDisplayNames
import com.boardbanker.core.model.PropertyDisplayNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuctionViewModel(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val propertyId: String,
    private val startedByPlayerId: String,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)
    private val bidIncrement = definitions.bankingValues.auctionBidIncrement
    private val auctionTimerSeconds = definitions.rules.auction.timedAuctionSeconds

    fun money(amount: Int): String = formatMoney(amount, definitions)

    private val _uiState = MutableStateFlow(AuctionUiState())
    val uiState: StateFlow<AuctionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuctionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuctionEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var scanPromptToken: Long = 0L
    private var auctionEndingPlayed = false
    private var auctionFinalized = false

    private val isEnergyGrid: Boolean = propertyId.startsWith("ENG_")

    init {
        val assetName = if (isEnergyGrid) {
            EnergyGridDisplayNames.displayNameWithNumber(propertyId, definitions)
        } else {
            PropertyDisplayNames.displayNameWithNumber(propertyId, definitions)
        }
        _uiState.update {
            it.copy(
                propertyId = propertyId,
                propertyName = assetName,
                remainingSeconds = auctionTimerSeconds,
                bidIncrement = bidIncrement,
            )
        }
        startAuctionIfNeeded()
    }

    private fun startAuctionIfNeeded() {
        val session = sessionManager.currentSession() ?: return
        val auction = session.auction
        if (auction != null && (auction.propertyId == propertyId || auction.energyGridId == propertyId)) {
            syncFromSession()
            resumeOrStartTimer()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true) }
            when (
                val outcome = executor.execute(
                    if (isEnergyGrid) {
                        GameCommand.StartAuction(energyGridId = propertyId, startedByPlayerId = startedByPlayerId)
                    } else {
                        GameCommand.StartAuction(propertyId = propertyId, startedByPlayerId = startedByPlayerId)
                    },
                )
            ) {
                is BankingCommitOutcome.Success -> {
                    GameplayOutcomeAudio.playCommittedOutcome(
                        gameAudioFeedback,
                        outcome.result,
                        session,
                        CommitAudioTrigger.AuctionStarted,
                    )
                    syncFromSession()
                    resumeOrStartTimer(resetDuration = true)
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to start auction.",
                        )
                    }
                }
            }
            _uiState.update { it.copy(commandInFlight = false) }
        }
    }

    private fun syncFromSession() {
        val session = sessionManager.currentSession() ?: return
        val auction = session.auction ?: return
        val bidderName = auction.currentBidderId?.let {
            PlayerDisplayNames.displayName(session, it, definitions)
        }
        _uiState.update {
            it.copy(
                currentBid = auction.currentBid,
                highestBidderId = auction.currentBidderId,
                highestBidderName = bidderName,
                auctionRunning = !auctionFinalized,
            )
        }
    }

    private fun resumeOrStartTimer(resetDuration: Boolean = false) {
        if (auctionFinalized || _uiState.value.result != null) return
        val existingEndsAt = savedStateHandle.get<Long>(auctionEndsAtKey())
        if (!resetDuration && existingEndsAt != null) {
            runTimerUntil(existingEndsAt)
            return
        }
        val endsAt = System.currentTimeMillis() + auctionTimerSeconds * 1_000L
        savedStateHandle[auctionEndsAtKey()] = endsAt
        runTimerUntil(endsAt)
    }

    private fun runTimerUntil(endsAtEpochMs: Long) {
        timerJob?.cancel()
        _uiState.update { state ->
            val remaining = remainingSecondsUntil(endsAtEpochMs)
            state.copy(
                remainingSeconds = remaining,
                auctionRunning = remaining > 0 && !auctionFinalized,
            )
        }
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = remainingSecondsUntil(endsAtEpochMs)
                _uiState.update { state ->
                    state.copy(
                        remainingSeconds = remaining,
                        auctionRunning = remaining > 0 && !auctionFinalized,
                    )
                }
                if (remaining <= 0) break
                delay(1_000)
            }
            onTimerExpired()
        }
    }

    private fun remainingSecondsUntil(endsAtEpochMs: Long): Int =
        ((endsAtEpochMs - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)

    private fun auctionEndsAtKey(): String = "auction_ends_at_$propertyId"

    private fun clearTimerState() {
        timerJob?.cancel()
        savedStateHandle.remove<Long>(auctionEndsAtKey())
    }

    fun onBidRequested() {
        if (!canAcceptBids()) return
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        _uiState.update { it.copy(awaitingBidScan = true) }
        _events.tryEmit(AuctionEvent.OpenScanner)
    }

    fun onPlayerScanned(playerId: String) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        if (!_uiState.value.awaitingBidScan || !canAcceptBids()) {
            _uiState.update { it.copy(awaitingBidScan = false) }
            return
        }
        val session = sessionManager.currentSession() ?: return
        val player = session.players[playerId]
        if (player == null) {
            _uiState.update { it.copy(message = "Unknown player card.", awaitingBidScan = false) }
            return
        }
        if (player.jailStatus) {
            val name = PlayerDisplayNames.displayName(session, playerId, definitions)
            InvalidUserActionAudio.notifyInvalidUserAction(gameAudioFeedback)
            _uiState.update {
                it.copy(
                    awaitingBidScan = false,
                    message = "PLAYER IN JAIL\n\n$name cannot participate\nin this Auction.",
                )
            }
            return
        }
        val nextBid = (session.auction?.currentBid ?: 0) + bidIncrement
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true, awaitingBidScan = false) }
            when (val outcome = executor.execute(GameCommand.PlaceAuctionBid(playerId, nextBid))) {
                is BankingCommitOutcome.Success -> {
                    syncFromSession()
                    resumeOrStartTimer(resetDuration = true)
                }
                is BankingCommitOutcome.Rejected -> {
                    InvalidUserActionAudio.notifyInvalidUserActionForGameError(
                        gameAudioFeedback,
                        outcome.result.error,
                    )
                    _uiState.update {
                        it.copy(message = outcome.result.error?.let { err -> err.toString() } ?: "Bid rejected.")
                    }
                }
                else -> _uiState.update { it.copy(message = "Unable to place bid.") }
            }
            _uiState.update { it.copy(commandInFlight = false) }
        }
    }

    private fun canAcceptBids(): Boolean =
        _uiState.value.auctionRunning &&
            !auctionFinalized &&
            _uiState.value.result == null &&
            !_uiState.value.commandInFlight &&
            sessionManager.currentSession()?.auction != null

    private fun onTimerExpired() {
        if (auctionFinalized) return
        val session = sessionManager.currentSession() ?: return
        val auction = session.auction ?: return
        auctionFinalized = true
        clearTimerState()
        if (auction.currentBidderId == null) {
            finalizeNoBidAuction()
        } else {
            completeAuction()
        }
    }

    private fun finalizeNoBidAuction() {
        if (auctionEndingPlayed) return
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true, auctionRunning = false) }
            val sessionBefore = sessionManager.currentSession() ?: return@launch
            when (val outcome = executor.execute(GameCommand.CancelAuction)) {
                is BankingCommitOutcome.Success -> {
                    auctionEndingPlayed = true
                    GameplayOutcomeAudio.playCommittedOutcome(
                        gameAudioFeedback,
                        outcome.result,
                        sessionBefore,
                        CommitAudioTrigger.AuctionEnding,
                    )
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            showNoBids = true,
                            auctionRunning = false,
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = "Unable to finalize auction.",
                        )
                    }
                }
            }
        }
    }

    fun onCancelBeforeFirstBid() {
        val session = sessionManager.currentSession() ?: return
        if ((session.auction?.currentBid ?: 0) > 0) return
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            returnToActiveGame()
        }
    }

    fun onRestartAuction() {
        auctionFinalized = false
        auctionEndingPlayed = false
        clearTimerState()
        _uiState.update { it.copy(showNoBids = false, result = null, auctionRunning = true) }
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            startAuctionIfNeeded()
        }
    }

    fun onLeaveUnowned() {
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            returnToActiveGame()
        }
    }

    private fun completeAuction() {
        if (auctionEndingPlayed) return
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true, auctionRunning = false) }
            val sessionBefore = sessionManager.currentSession()
            val winnerId = sessionBefore?.auction?.currentBidderId
            when (val outcome = executor.execute(GameCommand.CompleteAuction)) {
                is BankingCommitOutcome.Success -> {
                    if (sessionBefore != null) {
                        auctionEndingPlayed = true
                        GameplayOutcomeAudio.playCommittedOutcome(
                            gameAudioFeedback,
                            outcome.result,
                            sessionBefore,
                            CommitAudioTrigger.AuctionEnding,
                        )
                    }
                    val mapped = winnerId?.let {
                        resultMapper.mapAuctionWin(outcome.result, propertyId, it)
                    }
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            auctionRunning = false,
                            result = mapped,
                        )
                    }
                }
                is BankingCommitOutcome.DebtRequired -> {
                    _uiState.update { it.copy(commandInFlight = false, auctionRunning = false) }
                    _events.emit(AuctionEvent.NavigateToDebt)
                }
                is BankingCommitOutcome.Bankruptcy -> {
                    gameEndAudioCoordinator.onBankruptcyCommitted(gameAudioFeedback)
                    _uiState.update { it.copy(commandInFlight = false, auctionRunning = false) }
                    _events.emit(AuctionEvent.NavigateToGameOver)
                }
                is BankingCommitOutcome.Rejected -> {
                    auctionFinalized = false
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            message = outcome.result.error?.let { err -> err.toString() } ?: "Auction failed.",
                        )
                    }
                }
                else -> _uiState.update { it.copy(commandInFlight = false, message = "Unable to complete auction.") }
            }
        }
    }

    fun onDone() {
        clearTimerState()
        _uiState.update { it.copy(result = null, showNoBids = false, message = null) }
        returnToActiveGame()
    }

    private fun returnToActiveGame() {
        clearTimerState()
        _events.tryEmit(AuctionEvent.NavigateToActiveGame)
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

class AuctionViewModelFactory(
    private val sessionManager: ActiveGameSessionManager,
    private val definitions: GameDefinitions,
    private val propertyId: String,
    private val startedByPlayerId: String,
    private val gameAudioFeedback: GameAudioFeedback,
    private val gameEndAudioCoordinator: GameEndAudioCoordinator,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(AuctionViewModel::class.java)) {
            return AuctionViewModel(
                sessionManager,
                definitions,
                propertyId,
                startedByPlayerId,
                gameAudioFeedback,
                gameEndAudioCoordinator,
                extras.createSavedStateHandle(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
