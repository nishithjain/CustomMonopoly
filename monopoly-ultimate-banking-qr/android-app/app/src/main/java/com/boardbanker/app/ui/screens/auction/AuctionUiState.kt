package com.boardbanker.app.ui.screens.auction

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel

data class AuctionUiState(
    val propertyId: String = "",
    val propertyName: String = "",
    val currentBid: Int = 0,
    val highestBidderId: String? = null,
    val highestBidderName: String? = null,
    val remainingSeconds: Int = 0,
    val bidIncrement: Int = 0,
    val auctionRunning: Boolean = false,
    val commandInFlight: Boolean = false,
    val showNoBids: Boolean = false,
    val result: GameplayResultUiModel? = null,
    val message: String? = null,
    val awaitingBidScan: Boolean = false,
)

sealed class AuctionEvent {
    data object NavigateBack : AuctionEvent()
    data object OpenScanner : AuctionEvent()
    data object NavigateToDebt : AuctionEvent()
    data object NavigateToGameOver : AuctionEvent()
}
