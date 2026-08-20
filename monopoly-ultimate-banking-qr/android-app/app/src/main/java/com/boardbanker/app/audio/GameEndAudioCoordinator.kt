package com.boardbanker.app.audio

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates LostGame → Winner sequencing and prevents replay on resume.
 */
class GameEndAudioCoordinator(
    private val winnerDelayMs: Long = WINNER_DELAY_AFTER_LOST_GAME_MS,
    private val scheduleDelayed: (delayMs: Long, action: () -> Unit) -> Unit = { delayMs, action ->
        Handler(Looper.getMainLooper()).postDelayed(action, delayMs)
    },
) {
    private val freshGameEndPending = AtomicBoolean(false)
    private val winnerScheduled = AtomicBoolean(false)

    fun markFreshGameEndFromBankruptcy() {
        freshGameEndPending.set(true)
    }

    fun onBankruptcyCommitted(audio: GameAudioFeedback) {
        markFreshGameEndFromBankruptcy()
        audio.playLostGame()
    }

    fun onWinnerScreenPresented(audio: GameAudioFeedback) {
        if (!freshGameEndPending.compareAndSet(true, false)) return
        if (!winnerScheduled.compareAndSet(false, true)) return
        val complete = {
            audio.playWinner()
            winnerScheduled.set(false)
        }
        if (winnerDelayMs <= 0L) {
            complete()
        } else {
            scheduleDelayed(winnerDelayMs, complete)
        }
    }

    fun resetForNewGame() {
        freshGameEndPending.set(false)
        winnerScheduled.set(false)
    }

    companion object {
        const val WINNER_DELAY_AFTER_LOST_GAME_MS = 2_500L
    }
}
