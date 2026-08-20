package com.boardbanker.core.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardResolver

/**
 * Platform-neutral scan pipeline: duplicate suppression then card resolution.
 * Does not modify game state or execute GameCommands.
 */
class ScanProcessor(
    private val scanGate: ScanGate,
    private val cardResolver: CardResolver,
) {
    fun lock() = scanGate.lock()

    fun unlock() = scanGate.unlock()

    fun onNoQrDetected(nowMs: Long = System.currentTimeMillis()) {
        scanGate.onNoDetection(nowMs)
    }

    fun onQrPayload(payload: String, nowMs: Long = System.currentTimeMillis()): ScanProcessorResult {
        return when (val gateResult = scanGate.onDetection(payload, nowMs)) {
            is ScanGateResult.Ignored,
            is ScanGateResult.IgnoredDuplicate,
            is ScanGateResult.IgnoredLocked,
            -> ScanProcessorResult.Ignored(gateResult)

            is ScanGateResult.Accepted -> when (val resolution = cardResolver.resolve(gateResult.qrPayload)) {
                is CardResolution.Success -> ScanProcessorResult.CardResolved(resolution)
                is CardResolution.UnknownQr -> ScanProcessorResult.UnknownCard(resolution.qrPayload)
            }
        }
    }
}

sealed class ScanProcessorResult {
    data class Ignored(val reason: ScanGateResult) : ScanProcessorResult()
    data class CardResolved(val resolution: CardResolution.Success) : ScanProcessorResult()
    data class UnknownCard(val qrPayload: String) : ScanProcessorResult()
}
