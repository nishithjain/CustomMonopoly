package com.boardbanker.core.scanner

/**
 * Suppresses duplicate QR detections while a code remains in view.
 * Supports explicit workflow locking until the UI acknowledges a scan.
 */
class ScanGate(
    private val noDetectionReleaseMs: Long = 750L,
) {
    var scannerLocked: Boolean = false
        private set

    private var lastAcceptedPayload: String? = null
    private var lastSeenPayload: String? = null
    private var noDetectionStartMs: Long = 0L

    fun lock() {
        scannerLocked = true
    }

    fun unlock() {
        scannerLocked = false
        lastAcceptedPayload = null
        lastSeenPayload = null
        noDetectionStartMs = 0L
    }

    fun onNoDetection(nowMs: Long = System.currentTimeMillis()) {
        if (lastSeenPayload == null) {
            return
        }
        if (noDetectionStartMs == 0L) {
            noDetectionStartMs = nowMs
        }
        if (nowMs - noDetectionStartMs >= noDetectionReleaseMs) {
            lastSeenPayload = null
            lastAcceptedPayload = null
            noDetectionStartMs = 0L
        }
    }

    fun onDetection(payload: String, nowMs: Long = System.currentTimeMillis()): ScanGateResult {
        val normalized = payload.trim()
        noDetectionStartMs = 0L

        if (normalized.isEmpty()) {
            onNoDetection(nowMs)
            return ScanGateResult.Ignored
        }

        lastSeenPayload = normalized

        if (scannerLocked) {
            return ScanGateResult.IgnoredLocked
        }

        if (normalized == lastAcceptedPayload) {
            return ScanGateResult.IgnoredDuplicate
        }

        lastAcceptedPayload = normalized
        return ScanGateResult.Accepted(normalized)
    }
}

sealed class ScanGateResult {
    data class Accepted(val qrPayload: String) : ScanGateResult()
    data object IgnoredDuplicate : ScanGateResult()
    data object IgnoredLocked : ScanGateResult()
    data object Ignored : ScanGateResult()
}
