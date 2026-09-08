package com.boardbanker.app.audio

/**
 * Application-layer audio feedback for QR scans, invalid user actions, and gameplay outcomes.
 *
 * Audio does not mutate [com.boardbanker.core.model.GameSession] or execute [com.boardbanker.core.command.GameCommand].
 */
interface GameAudioFeedback {
    var enabled: Boolean

    fun playUserCard(playerId: String)

    fun playError()

    /** User identification clip, then error feedback (sequenced, not overlapping). */
    fun playUserCardThenError(playerId: String)

    fun playScanPrompt()

    /** Successful QR recognition for non-user cards (property, event, energy grid). */
    fun playScanAccepted()

    fun playGameStarted()

    fun playPropertyPurchased()

    fun playEnergyGridPurchased()

    fun playColorSetComplete()

    fun playRentTransfer()

    fun playRentRelief()

    fun playRentLevelIncreased()

    fun playRentLevelDecreased()

    fun playPropertySold()

    fun playGo()

    fun playLocation()

    fun playGoToJail()

    fun playJailRelease()

    fun playJailPass()

    fun playAuctionBegins()

    fun playAuctionEnding()

    fun playBankCredit()

    fun playBankDebit()

    fun playMoneyTransfer()

    fun playEventApplied()

    fun playTurnChanged()

    fun playTurnSkipped()

    fun playExtraTurn()

    fun playDiceRoll()

    fun playMovePlayer()

    fun playLuckyDraw()

    /**
     * Plays short gameplay cues in order without overlap.
     */
    fun playSoundSequence(steps: List<() -> Unit>, gapBetweenMs: Long = DEFAULT_SEQUENCE_GAP_MS)

    fun playUndo()

    fun playUndoLastAction()

    fun playLostGame()

    fun playWinner()

    fun release()

    companion object {
        const val DEFAULT_SEQUENCE_GAP_MS = 500L
    }
}
