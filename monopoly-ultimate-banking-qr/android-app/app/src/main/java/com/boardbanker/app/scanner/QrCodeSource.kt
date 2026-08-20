package com.boardbanker.app.scanner

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over QR input sources. Emits decoded payload strings only — no ML Kit types.
 */
interface QrCodeSource {
    val detections: Flow<QrDetectionEvent>
    fun start()
    fun stop()
}

sealed class QrDetectionEvent {
    data class QrDetected(val payload: String) : QrDetectionEvent()
    data object NoQrDetected : QrDetectionEvent()
}
