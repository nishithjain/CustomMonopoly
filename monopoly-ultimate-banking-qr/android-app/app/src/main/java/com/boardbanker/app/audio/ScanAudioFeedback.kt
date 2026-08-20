package com.boardbanker.app.audio

import android.util.Log
import com.boardbanker.app.BuildConfig
import com.boardbanker.app.scanner.CardTypeValidation
import com.boardbanker.core.card.CardType
import com.boardbanker.core.scanner.ScanProcessorResult

/**
 * Central scan audio trigger after ScanGate acceptance and CardResolver resolution.
 *
 * Audio failures are logged and never propagate — workflow delivery must not depend on sound.
 */
object ScanAudioFeedback {
    private const val TAG = "ScanAudioFeedback"

    fun onScanProcessed(
        audio: GameAudioFeedback,
        result: ScanProcessorResult,
        validation: CardTypeValidation?,
        scanAttemptId: Long? = null,
    ) {
        try {
            when (result) {
                is ScanProcessorResult.Ignored -> Unit
                is ScanProcessorResult.UnknownCard -> audio.playError()
                is ScanProcessorResult.CardResolved -> {
                    val resolution = result.resolution
                    when (validation) {
                        CardTypeValidation.Accepted -> {
                            if (resolution.cardType == CardType.USER) {
                                logUserAudio(scanAttemptId, resolution.cardId)
                                audio.playUserCard(resolution.cardId)
                            }
                        }
                        is CardTypeValidation.WrongType -> {
                            if (resolution.cardType == CardType.USER) {
                                logUserAudio(scanAttemptId, resolution.cardId)
                                audio.playUserCardThenError(resolution.cardId)
                            } else {
                                audio.playError()
                            }
                        }
                        null -> {
                            if (resolution.cardType == CardType.USER) {
                                logUserAudio(scanAttemptId, resolution.cardId)
                                audio.playUserCard(resolution.cardId)
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                try {
                    Log.w(TAG, "Audio playback failed; scan delivery continues", ex)
                } catch (_: RuntimeException) {
                    // android.util.Log is not mocked in JVM unit tests.
                }
            }
        }
    }

    private fun logUserAudio(scanAttemptId: Long?, cardId: String) {
        if (scanAttemptId != null) {
            com.boardbanker.app.scanner.delivery.ScanDeliveryTrace.log(
                scanAttemptId,
                com.boardbanker.app.scanner.delivery.ScanDeliveryStage.USER_AUDIO_REQUESTED,
                "cardId=$cardId",
            )
        }
    }
}
