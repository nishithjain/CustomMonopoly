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

    fun playGameStarted()

    fun playPropertyPurchased()

    fun playColorSetComplete()

    fun playRentTransfer()

    fun playRentLevelIncreased()

    fun playRentLevelDecreased()

    fun playGo()

    fun playGoToJail()

    fun playJail()

    fun playAuctionBegins()

    fun playAuctionEnding()

    fun playKaChing()

    fun playMoneyLost()

    fun playUndo()

    fun playLostGame()

    fun playWinner()

    fun release()
}
