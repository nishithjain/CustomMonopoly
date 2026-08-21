package com.boardbanker.app.ui.screens.auction

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
import com.boardbanker.app.banking.AuctionConfig
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.banking.BankingCommitOutcome
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameDefinitions
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
) : ViewModel() {
    private val executor = BankingCommandExecutor(sessionManager)
    private val resultMapper = BankingResultMapper(definitions)
    private val bidIncrement = definitions.bankingValues.auctionBidIncrement

    fun money(amount: Int): String = formatMoney(amount, definitions)

    private val _uiState = MutableStateFlow(AuctionUiState())
    val uiState: StateFlow<AuctionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuctionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuctionEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var scanPromptToken: Long = 0L
    private var auctionEndingPlayed = false

    init {
        val propertyName = definitions.properties[propertyId]?.name ?: propertyId
        _uiState.update {
            it.copy(
                propertyId = propertyId,
                propertyName = propertyName,
                remainingSeconds = AuctionConfig.TIMER_SECONDS,
                bidIncrement = bidIncrement,
            )
        }
        startAuctionIfNeeded()
    }

    private fun startAuctionIfNeeded() {
        val session = sessionManager.currentSession() ?: return
        if (session.auction?.propertyId == propertyId) {
            syncFromSession()
            startTimer()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true) }
            when (val outcome = executor.execute(GameCommand.StartAuction(propertyId, startedByPlayerId))) {
                is BankingCommitOutcome.Success -> {
                    GameplayOutcomeAudio.playCommittedOutcome(
                        gameAudioFeedback,
                        outcome.result,
                        session,
                        CommitAudioTrigger.AuctionStarted,
                    )
                    syncFromSession()
                    startTimer()
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
                auctionRunning = true,
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                remainingSeconds = AuctionConfig.TIMER_SECONDS,
                auctionRunning = true,
            )
        }
        timerJob = viewModelScope.launch {
            var remaining = AuctionConfig.TIMER_SECONDS
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _uiState.update { state -> state.copy(remainingSeconds = remaining) }
            }
            onTimerExpired()
        }
    }

    fun onBidRequested() {
        scanPromptToken = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(gameAudioFeedback, scanPromptToken)
        _uiState.update { it.copy(awaitingBidScan = true) }
        _events.tryEmit(AuctionEvent.OpenScanner)
    }

    fun onPlayerScanned(playerId: String) {
        ScanPromptAudio.endPromptSession(scanPromptToken)
        if (!_uiState.value.awaitingBidScan) return
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
                    startTimer()
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

    private fun onTimerExpired() {
        if (!auctionEndingPlayed) {
            auctionEndingPlayed = true
            GameplayOutcomeAudio.playCue(gameAudioFeedback, GameplayAudioCue.AUCTION_ENDING)
        }
        val session = sessionManager.currentSession() ?: return
        val auction = session.auction ?: return
        if (auction.currentBidderId == null) {
            _uiState.update { it.copy(showNoBids = true, auctionRunning = false) }
            return
        }
        completeAuction()
    }

    fun onCancelBeforeFirstBid() {
        val session = sessionManager.currentSession() ?: return
        if ((session.auction?.currentBid ?: 0) > 0) return
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            _events.emit(AuctionEvent.NavigateBack)
        }
    }

    fun onRestartAuction() {
        _uiState.update { it.copy(showNoBids = false) }
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            startAuctionIfNeeded()
        }
    }

    fun onLeaveUnowned() {
        viewModelScope.launch {
            executor.execute(GameCommand.CancelAuction)
            _events.emit(AuctionEvent.NavigateBack)
        }
    }

    private fun completeAuction() {
        viewModelScope.launch {
            _uiState.update { it.copy(commandInFlight = true) }
            val sessionBefore = sessionManager.currentSession()
            val winnerId = sessionBefore?.auction?.currentBidderId
            when (val outcome = executor.execute(GameCommand.CompleteAuction)) {
                is BankingCommitOutcome.Success -> {
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
        _events.tryEmit(AuctionEvent.NavigateBack)
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuctionViewModel::class.java)) {
            return AuctionViewModel(
                sessionManager,
                definitions,
                propertyId,
                startedByPlayerId,
                gameAudioFeedback,
                gameEndAudioCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
