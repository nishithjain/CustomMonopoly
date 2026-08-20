package com.boardbanker.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGateTest {
    @Test
    fun firstDetectionAccepted() {
        val gate = ScanGate()
        val result = gate.onDetection("MUB:P:01", nowMs = 1_000L)
        assertTrue(result is ScanGateResult.Accepted)
        assertEquals("MUB:P:01", (result as ScanGateResult.Accepted).qrPayload)
    }

    @Test
    fun immediateDuplicatesProduceOneAcceptance() {
        val gate = ScanGate()
        var accepted = 0
        listOf(1_000L, 1_010L, 1_020L).forEach { t ->
            if (gate.onDetection("MUB:P:01", nowMs = t) is ScanGateResult.Accepted) {
                accepted++
            }
        }
        assertEquals(1, accepted)
    }

    @Test
    fun sameQrStillVisibleRemainsIgnored() {
        val gate = ScanGate()
        gate.onDetection("MUB:P:01", nowMs = 1_000L)
        val second = gate.onDetection("MUB:P:01", nowMs = 1_500L)
        assertTrue(second is ScanGateResult.IgnoredDuplicate)
    }

    @Test
    fun cardLeavesFrameThenSameCardAcceptedAgain() {
        val gate = ScanGate(noDetectionReleaseMs = 100L)
        gate.onDetection("MUB:P:01", nowMs = 1_000L)
        gate.onNoDetection(nowMs = 1_050L)
        gate.onNoDetection(nowMs = 1_200L)
        val result = gate.onDetection("MUB:P:01", nowMs = 1_300L)
        assertTrue(result is ScanGateResult.Accepted)
    }

    @Test
    fun differentQrWhileUnlockedAccepted() {
        val gate = ScanGate()
        gate.onDetection("MUB:P:01", nowMs = 1_000L)
        gate.onNoDetection(nowMs = 1_050L)
        gate.onNoDetection(nowMs = 1_900L)
        val result = gate.onDetection("MUB:P:02", nowMs = 2_000L)
        assertTrue(result is ScanGateResult.Accepted)
    }

    @Test
    fun lockedScannerIgnoresNewDetection() {
        val gate = ScanGate()
        gate.lock()
        val result = gate.onDetection("MUB:P:02", nowMs = 1_000L)
        assertTrue(result is ScanGateResult.IgnoredLocked)
    }

    @Test
    fun unlockAllowsScanAgain() {
        val gate = ScanGate()
        gate.onDetection("MUB:P:01", nowMs = 1_000L)
        gate.lock()
        gate.unlock()
        val result = gate.onDetection("MUB:P:01", nowMs = 2_000L)
        assertTrue(result is ScanGateResult.Accepted)
    }
}
