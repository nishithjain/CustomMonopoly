package com.boardbanker.app.audio

/**
 * Test double that records audio invocations without speaker output.
 */
class RecordingGameAudioFeedback : GameAudioFeedback {
    override var enabled: Boolean = true

    val userCardCalls = mutableListOf<String>()
    val errorCalls = mutableListOf<Unit>()
    val userThenErrorCalls = mutableListOf<String>()
    val gameplayCalls = mutableListOf<String>()

    override fun playUserCard(playerId: String) {
        if (!enabled) return
        userCardCalls.add(playerId)
    }

    override fun playError() {
        if (!enabled) return
        errorCalls.add(Unit)
    }

    override fun playUserCardThenError(playerId: String) {
        if (!enabled) return
        userThenErrorCalls.add(playerId)
    }

    override fun playScanPrompt() = record("SCAN_CARD")

    override fun playGameStarted() = record("GAME_STARTS")

    override fun playPropertyPurchased() = record("PROPERTY_PURCHASED")

    override fun playColorSetComplete() = record("COLOR_SET_COMPLETE")

    override fun playRentTransfer() = record("RENT_TRANSFER")

    override fun playRentLevelIncreased() = record("RENT_LEVEL_INCREASED")

    override fun playRentLevelDecreased() = record("RENT_LEVEL_DECREASED")

    override fun playGo() = record("GO")

    override fun playGoToJail() = record("GO_TO_JAIL")

    override fun playJail() = record("JAIL")

    override fun playAuctionBegins() = record("AUCTION_BEGINS")

    override fun playAuctionEnding() = record("AUCTION_ENDING")

    override fun playKaChing() = record("KA_CHING")

    override fun playMoneyLost() = record("MONEY_LOST")

    override fun playUndo() = record("UNDO")

    override fun playLostGame() = record("LOST_GAME")

    override fun playWinner() = record("WINNER")

    override fun release() = Unit

    private fun record(name: String) {
        if (!enabled) return
        gameplayCalls.add(name)
    }

    fun reset() {
        userCardCalls.clear()
        errorCalls.clear()
        userThenErrorCalls.clear()
        gameplayCalls.clear()
    }
}
