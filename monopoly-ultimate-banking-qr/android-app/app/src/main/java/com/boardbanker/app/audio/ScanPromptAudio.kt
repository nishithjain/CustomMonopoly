package com.boardbanker.app.audio

/**
 * Plays [GameSound.SCAN_CARD] once per scan prompt token.
 */
object ScanPromptAudio {
    private val lock = Any()
    private var activeToken: Long = -1L

    fun playOnce(audio: GameAudioFeedback, promptToken: Long) {
        if (promptToken <= 0L) return
        synchronized(lock) {
            if (activeToken == promptToken) return
            activeToken = promptToken
        }
        audio.playScanPrompt()
    }

    fun beginPromptSession(): Long = System.nanoTime()

    fun endPromptSession(promptToken: Long) {
        synchronized(lock) {
            if (activeToken == promptToken) {
                activeToken = -1L
            }
        }
    }

    fun resetForTests() {
        synchronized(lock) {
            activeToken = -1L
        }
    }
}
