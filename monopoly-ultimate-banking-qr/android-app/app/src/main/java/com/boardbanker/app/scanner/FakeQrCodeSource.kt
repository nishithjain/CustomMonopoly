package com.boardbanker.app.scanner

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Test double that emits scripted QR detection events without a physical camera.
 */
class FakeQrCodeSource(
    private val scriptedEvents: List<FakeQrEvent> = emptyList(),
) : QrCodeSource {
    private val _detections = MutableSharedFlow<QrDetectionEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val detections: SharedFlow<QrDetectionEvent> = _detections.asSharedFlow()

    override fun start() {
        scriptedEvents.forEach { event ->
            when (event) {
                is FakeQrEvent.Detected -> _detections.tryEmit(QrDetectionEvent.QrDetected(event.payload))
                FakeQrEvent.NoDetection -> _detections.tryEmit(QrDetectionEvent.NoQrDetected)
            }
        }
    }

    override fun stop() = Unit

    fun emit(payload: String) {
        _detections.tryEmit(QrDetectionEvent.QrDetected(payload))
    }

    fun emitNoDetection() {
        _detections.tryEmit(QrDetectionEvent.NoQrDetected)
    }
}

sealed class FakeQrEvent {
    data class Detected(val payload: String) : FakeQrEvent()
    data object NoDetection : FakeQrEvent()
}
