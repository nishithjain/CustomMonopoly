package com.boardbanker.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/**
 * SoundPool-backed [GameAudioFeedback].
 *
 * Short operation sounds replace each other. User→error sequences are timed.
 * Major end-game sounds are queued by [GameEndAudioCoordinator].
 */
class SoundPoolGameAudioFeedback(
    context: Context,
    private val userSoundDurationMs: Long = DEFAULT_USER_SOUND_DURATION_MS,
) : GameAudioFeedback {
    override var enabled: Boolean = true

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<String, Int>()
    private val activeStreams = mutableListOf<Int>()
    private var pendingErrorRunnable: Runnable? = null

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()

        GameSoundRegistry.allSounds.forEach { sound ->
            val resourceName = GameSoundRegistry.resourceNameFor(sound)
            val resourceId = resourceIdFor(resourceName) ?: return@forEach
            loadSound(resourceName, resourceId)
        }
    }

    override fun playUserCard(playerId: String) {
        if (!enabled) return
        cancelPendingSequence()
        stopActiveStreams()
        val resourceName = UserCardSoundRegistry.soundResourceNameFor(playerId) ?: return
        playSound(resourceName)
    }

    override fun playError() {
        if (!enabled) return
        cancelPendingSequence()
        stopActiveStreams()
        playSound(UserCardSoundRegistry.ERROR_SOUND)
    }

    override fun playUserCardThenError(playerId: String) {
        if (!enabled) return
        cancelPendingSequence()
        stopActiveStreams()
        val resourceName = UserCardSoundRegistry.soundResourceNameFor(playerId) ?: run {
            playError()
            return
        }
        playSound(resourceName)
        val runnable = Runnable { playErrorInternal() }
        pendingErrorRunnable = runnable
        mainHandler.postDelayed(runnable, userSoundDurationMs)
    }

    override fun playScanPrompt() = playOperationSound(GameSound.SCAN_CARD)

    override fun playGameStarted() = playMajorSound(GameSound.GAME_STARTS)

    override fun playPropertyPurchased() = playOperationSound(GameSound.PROPERTY_PURCHASED)

    override fun playColorSetComplete() = playOperationSound(GameSound.COLOR_SET_COMPLETE)

    override fun playRentTransfer() = playOperationSound(GameSound.RENT_TRANSFER)

    override fun playRentLevelIncreased() = playOperationSound(GameSound.RENT_LEVEL_INCREASED)

    override fun playRentLevelDecreased() = playOperationSound(GameSound.RENT_LEVEL_DECREASED)

    override fun playGo() = playOperationSound(GameSound.GO)

    override fun playGoToJail() = playOperationSound(GameSound.GO_TO_JAIL)

    override fun playJail() = playOperationSound(GameSound.JAIL)

    override fun playAuctionBegins() = playMajorSound(GameSound.AUCTION_BEGINS)

    override fun playAuctionEnding() = playMajorSound(GameSound.AUCTION_ENDING)

    override fun playKaChing() = playOperationSound(GameSound.KA_CHING)

    override fun playMoneyLost() = playOperationSound(GameSound.MONEY_LOST)

    override fun playUndo() = playOperationSound(GameSound.UNDO)

    override fun playLostGame() = playMajorSound(GameSound.LOST_GAME)

    override fun playWinner() = playMajorSound(GameSound.WINNER)

    override fun release() {
        cancelPendingSequence()
        stopActiveStreams()
        soundPool.release()
    }

    private fun playOperationSound(sound: GameSound) {
        if (!enabled) return
        cancelPendingSequence()
        stopActiveStreams()
        playSound(GameSoundRegistry.resourceNameFor(sound))
    }

    private fun playMajorSound(sound: GameSound) {
        if (!enabled) return
        cancelPendingSequence()
        stopActiveStreams()
        playSound(GameSoundRegistry.resourceNameFor(sound))
    }

    private fun playErrorInternal() {
        pendingErrorRunnable = null
        if (!enabled) return
        playSound(UserCardSoundRegistry.ERROR_SOUND)
    }

    private fun playSound(resourceName: String) {
        val soundId = soundIds[resourceName] ?: return
        val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        if (streamId != 0) {
            activeStreams.add(streamId)
        }
    }

    private fun loadSound(key: String, resourceId: Int) {
        soundIds[key] = soundPool.load(appContext, resourceId, 1)
    }

    private fun resourceIdFor(resourceName: String): Int? {
        val id = appContext.resources.getIdentifier(resourceName, "raw", appContext.packageName)
        return id.takeIf { it != 0 }
    }

    private fun cancelPendingSequence() {
        pendingErrorRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingErrorRunnable = null
    }

    private fun stopActiveStreams() {
        activeStreams.forEach { streamId ->
            soundPool.stop(streamId)
        }
        activeStreams.clear()
    }

    companion object {
        const val DEFAULT_USER_SOUND_DURATION_MS = 500L
    }
}
