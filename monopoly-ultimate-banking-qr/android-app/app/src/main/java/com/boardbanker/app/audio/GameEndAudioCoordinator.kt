package com.boardbanker.app.audio

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates LostGame → Winner sequencing and prevents replay on resume.
 */
class GameEndAudioCoordinator(
    private val winnerDelayMs: Long = WINNER_DELAY_AFTER_LOST_GAME_MS,
    private val scheduleDelayed: (delayMs: Long, action: () -> Unit) -> Unit = { delayMs, action ->
        if (delayMs <= 0L) {
            action()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs)
        }
    },
) {
    private val winnerPending = AtomicBoolean(false)
    private val winnerPlayed = AtomicBoolean(false)
    private val lostGamePlayedThisGameEnd = AtomicBoolean(false)

    fun onBankruptcyCommitted(audio: GameAudioFeedback) {
        if (lostGamePlayedThisGameEnd.compareAndSet(false, true)) {
            audio.playLostGame()
        }
        winnerPending.set(true)
    }

    fun onGameConcludedForWinnerPresentation() {
        winnerPending.set(true)
    }

    fun onWinnerScreenPresented(audio: GameAudioFeedback) {
        if (!winnerPending.compareAndSet(true, false)) return
        if (!winnerPlayed.compareAndSet(false, true)) return
        val playWinner = {
            audio.playWinner()
        }
        if (lostGamePlayedThisGameEnd.get() && winnerDelayMs > 0L) {
            scheduleDelayed(winnerDelayMs, playWinner)
        } else {
            playWinner()
        }
    }

    fun resetForNewGame() {
        winnerPending.set(false)
        winnerPlayed.set(false)
        lostGamePlayedThisGameEnd.set(false)
    }

    companion object {
        const val WINNER_DELAY_AFTER_LOST_GAME_MS = 2_500L
    }
}
