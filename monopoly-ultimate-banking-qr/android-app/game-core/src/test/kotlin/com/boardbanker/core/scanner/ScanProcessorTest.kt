package com.boardbanker.core.scanner

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.card.DefaultCardResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanProcessorTest {
    private val processor = ScanProcessor(
        scanGate = ScanGate(),
        cardResolver = DefaultCardResolver(TestFixtures.definitions),
    )

    @Test
    fun fakePayloadResolvesToCardResolved() {
        val result = processor.onQrPayload("MUB:P:01")
        assertTrue(result is ScanProcessorResult.CardResolved)
        val resolved = result as ScanProcessorResult.CardResolved
        assertEquals("PRP_01", resolved.resolution.cardId)
    }

    @Test
    fun unknownPayloadReturnsUnknownCard() {
        val result = processor.onQrPayload("https://example.com")
        assertTrue(result is ScanProcessorResult.UnknownCard)
    }

    @Test
    fun duplicatePayloadsIgnoredAfterFirstAcceptance() {
        var accepted = 0
        repeat(3) {
            if (processor.onQrPayload("MUB:E:E01") is ScanProcessorResult.CardResolved) {
                accepted++
            }
        }
        assertEquals(1, accepted)
    }

    @Test
    fun lockedProcessorIgnoresNewPayload() {
        processor.onQrPayload("MUB:P:01")
        processor.lock()
        val result = processor.onQrPayload("MUB:P:02")
        assertTrue(result is ScanProcessorResult.Ignored)
    }
}
