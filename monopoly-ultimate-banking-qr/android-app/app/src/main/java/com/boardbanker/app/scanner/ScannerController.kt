package com.boardbanker.app.scanner

import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.scanner.ScanGate
import com.boardbanker.core.scanner.ScanProcessor
import com.boardbanker.core.scanner.ScanProcessorResult

/**
 * Coordinates ScanGate + CardResolver without touching GameSession or GameEngine.
 */
class ScannerController(
    definitions: GameDefinitions,
) {
    private val scanGate = ScanGate()
    private val scanProcessor = ScanProcessor(scanGate, DefaultCardResolver(definitions))

    fun onQrPayload(payload: String, nowMs: Long = System.currentTimeMillis()): ScanProcessorResult =
        scanProcessor.onQrPayload(payload, nowMs)

    fun onNoQrDetected(nowMs: Long = System.currentTimeMillis()) {
        scanProcessor.onNoQrDetected(nowMs)
    }

    fun lockAfterResolved() {
        scanProcessor.lock()
    }

    fun prepareForNextScan() {
        scanProcessor.unlock()
    }
}
