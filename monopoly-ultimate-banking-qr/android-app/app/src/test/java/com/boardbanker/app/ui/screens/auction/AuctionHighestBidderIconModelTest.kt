package com.boardbanker.app.ui.screens.auction

import org.junit.Assert.assertEquals
import org.junit.Test

class AuctionHighestBidderIconModelTest {
    @Test
    fun highestBidderUsesStablePlayerId() {
        val state = AuctionUiState(
            highestBidderId = "USR_03",
            highestBidderName = "Ship",
        )
        assertEquals("USR_03", state.highestBidderId)
        assertEquals("Ship", state.highestBidderName)
    }
}
