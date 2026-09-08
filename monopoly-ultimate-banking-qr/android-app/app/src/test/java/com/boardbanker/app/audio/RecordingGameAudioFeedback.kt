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

    override fun playScanAccepted() = record("SCAN_CARD")

    override fun playGameStarted() = record("GAME_STARTS")

    override fun playPropertyPurchased() = record("PROPERTY_PURCHASED")

    override fun playEnergyGridPurchased() = record("ENERGY_GRID_PURCHASED")

    override fun playColorSetComplete() = record("COLOR_SET_COMPLETE")

    override fun playRentTransfer() = record("RENT_TRANSFER")

    override fun playRentRelief() = record("RENT_RELIEF")

    override fun playRentLevelIncreased() = record("RENT_LEVEL_INCREASED")

    override fun playRentLevelDecreased() = record("RENT_LEVEL_DECREASED")

    override fun playPropertySold() = record("PROPERTY_SOLD")

    override fun playGo() = record("GO")

    override fun playLocation() = record("LOCATION")

    override fun playGoToJail() = record("GO_TO_JAIL")

    override fun playJailRelease() = record("JAIL_RELEASE")

    override fun playJailPass() = record("JAIL_PASS")

    override fun playAuctionBegins() = record("AUCTION_BEGINS")

    override fun playAuctionEnding() = record("AUCTION_ENDING")

    override fun playBankCredit() = record("BANK_CREDIT")

    override fun playBankDebit() = record("BANK_DEBIT")

    override fun playMoneyTransfer() = record("MONEY_TRANSFER")

    override fun playEventApplied() = record("EVENT_APPLIED")

    override fun playTurnChanged() = record("TURN_CHANGED")

    override fun playTurnSkipped() = record("TURN_SKIPPED")

    override fun playExtraTurn() = record("EXTRA_TURN")

    override fun playDiceRoll() = record("DICE_ROLL")

    override fun playMovePlayer() = record("MOVE_PLAYER")

    override fun playLuckyDraw() = record("LUCKY_DRAW")

    override fun playSoundSequence(steps: List<() -> Unit>, gapBetweenMs: Long) {
        steps.forEach { it() }
    }

    override fun playUndo() = record("UNDO")

    override fun playUndoLastAction() = record("UNDO_LAST_ACTION")

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
