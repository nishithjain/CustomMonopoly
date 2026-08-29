package com.boardbanker.app.scanner.delivery

import android.util.Log
import com.boardbanker.app.BuildConfig
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.scanner.model.ResolvedCard

enum class ScanResultConsumer {
    PLAYER_SETUP,
    GAME,
    BANKING,
    AUCTION,
    DEBT,
    PLAYER_DETAILS,
}

enum class ScanDeliveryStage {
    SCAN_DETECTED,
    SCAN_GATE_ACCEPTED,
    CARD_RESOLVED,
    RESULT_STAGED,
    RESULT_EMITTED,
    RESULT_RECEIVED_BY_CALLER,
    WORKFLOW_CONSUMED,
    USER_AUDIO_REQUESTED,
}

object ScanDeliveryTrace {
    private const val TAG = "ScanDelivery"

    fun log(scanAttemptId: Long, stage: ScanDeliveryStage, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        val suffix = if (detail.isEmpty()) "" else " $detail"
        try {
            Log.d(TAG, "ScanAttempt $scanAttemptId: ${stage.name}$suffix")
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in JVM unit tests.
        }
    }
}

data class ScanDeliveryResult(
    val scanAttemptId: Long,
    val consumer: ScanResultConsumer,
    val card: ResolvedCard,
)

/**
 * Reliable one-shot scan result delivery across navigation transitions.
 *
 * Uses SharedFlow replay so a parent that temporarily leaves composition during scanning
 * still receives the staged [ResolvedCard] when it resumes collecting.
 */
class ScanResultDeliverer {
    private val lock = Any()
    private var nextAttemptId: Long = 1L
    private var preparedConsumer: ScanResultConsumer? = null
    private var pendingScanRequest: ScanRequest? = null
    private var lastConsumedAttemptId: Long = -1L
    private var lastStagedAttemptId: Long = -1L

    private val _deliveries = kotlinx.coroutines.flow.MutableSharedFlow<ScanDeliveryResult>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val deliveries: kotlinx.coroutines.flow.SharedFlow<ScanDeliveryResult> = _deliveries

    fun nextScanAttemptId(): Long = synchronized(lock) {
        nextAttemptId++
    }

    fun prepareConsumer(
        consumer: ScanResultConsumer,
        request: ScanRequest = ScanRequest.gameCard(),
    ) {
        synchronized(lock) {
            preparedConsumer = consumer
            pendingScanRequest = request
        }
    }

    fun peekScanRequest(): ScanRequest? = synchronized(lock) { pendingScanRequest }

    fun clearPendingScanRequest() {
        synchronized(lock) {
            pendingScanRequest = null
        }
    }

    fun stageResolvedCard(scanAttemptId: Long, card: ResolvedCard): Boolean {
        val consumer = synchronized(lock) {
            preparedConsumer ?: return false
        }
        val delivery = ScanDeliveryResult(
            scanAttemptId = scanAttemptId,
            consumer = consumer,
            card = card,
        )
        ScanDeliveryTrace.log(scanAttemptId, ScanDeliveryStage.RESULT_STAGED, "cardId=${card.cardId}")
        val emitted = _deliveries.tryEmit(delivery)
        if (emitted) {
            synchronized(lock) {
                lastStagedAttemptId = scanAttemptId
            }
            ScanDeliveryTrace.log(scanAttemptId, ScanDeliveryStage.RESULT_EMITTED, "consumer=$consumer")
        }
        return emitted
    }

    fun tryConsume(scanAttemptId: Long, expectedConsumer: ScanResultConsumer): ResolvedCard? {
        synchronized(lock) {
            if (scanAttemptId <= lastConsumedAttemptId) return null
            val replay = _deliveries.replayCache.lastOrNull() ?: return null
            if (replay.scanAttemptId != scanAttemptId || replay.consumer != expectedConsumer) return null
            lastConsumedAttemptId = scanAttemptId
            preparedConsumer = null
            pendingScanRequest = null
            return replay.card
        }
    }

    fun peekPendingFor(expectedConsumer: ScanResultConsumer): ScanDeliveryResult? {
        synchronized(lock) {
            val replay = _deliveries.replayCache.lastOrNull() ?: return null
            if (replay.scanAttemptId <= lastConsumedAttemptId) return null
            if (replay.consumer != expectedConsumer) return null
            return replay
        }
    }

    fun resetForTests() {
        synchronized(lock) {
            nextAttemptId = 1L
            preparedConsumer = null
            pendingScanRequest = null
            lastConsumedAttemptId = -1L
            lastStagedAttemptId = -1L
        }
    }
}
