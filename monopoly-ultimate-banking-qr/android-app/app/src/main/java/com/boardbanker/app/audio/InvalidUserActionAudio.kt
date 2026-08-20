package com.boardbanker.app.audio

import com.boardbanker.core.error.GameError

/**
 * Determines when structured domain errors represent invalid user actions
 * (as opposed to valid rule consequences such as debt resolution).
 */
object InvalidUserActionAudio {
    fun shouldPlayForGameError(error: GameError?): Boolean {
        if (error == null) return true
        return when (error) {
            is GameError.InsufficientFunds -> false
            else -> true
        }
    }

    fun notifyInvalidUserAction(audio: GameAudioFeedback) {
        audio.playError()
    }

    fun notifyInvalidUserActionForGameError(audio: GameAudioFeedback, error: GameError?) {
        if (shouldPlayForGameError(error)) {
            audio.playError()
        }
    }
}
