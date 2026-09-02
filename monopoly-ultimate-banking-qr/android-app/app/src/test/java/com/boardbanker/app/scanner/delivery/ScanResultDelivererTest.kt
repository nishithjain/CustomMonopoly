package com.boardbanker.app.scanner.delivery

import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanResultDelivererTest {
    private lateinit var deliverer: ScanResultDeliverer

    @Before
    fun setUp() {
        deliverer = ScanResultDeliverer()
    }

    private fun carCard() = ResolvedCard(
        cardId = "USR_01",
        cardType = CardType.USER,
        displayName = "Car",
        qrPayload = "MUB:PL:CAR",
    )

    @Test
    fun singleUserScanDeliversExactlyOnce() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.PLAYER_SETUP)
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()

        var consumed = 0
        val job = launch {
            val delivery = deliverer.deliveries.first()
            val resolved = deliverer.tryConsume(delivery.scanAttemptId, ScanResultConsumer.PLAYER_SETUP)
            if (resolved != null) consumed++
        }

        assertTrue(deliverer.stageResolvedCard(attemptId, card))
        job.join()

        assertEquals(1, consumed)
        assertNull(deliverer.peekPendingFor(ScanResultConsumer.PLAYER_SETUP))
    }

    @Test
    fun userCardDoesNotRequireSecondPhysicalScan() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.GAME)
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()

        assertTrue(deliverer.stageResolvedCard(attemptId, card))

        val consumed = deliverer.tryConsume(attemptId, ScanResultConsumer.GAME)
        assertEquals("USR_01", consumed?.cardId)
        assertNull(deliverer.tryConsume(attemptId, ScanResultConsumer.GAME))
    }

    @Test
    fun navigationTimingSurvivesDelayedCollector() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.GAME)
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(attemptId, card))

        val pending = deliverer.peekPendingFor(ScanResultConsumer.GAME)
        assertEquals("USR_01", pending?.card?.cardId)

        val resolved = deliverer.tryConsume(attemptId, ScanResultConsumer.GAME)
        assertEquals("USR_01", resolved?.cardId)
    }

    @Test
    fun slowCallerReceivesReplayedResult() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.BANKING)
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(attemptId, card))

        var received: ResolvedCard? = null
        val job = launch {
            val delivery = deliverer.deliveries.first { it.consumer == ScanResultConsumer.BANKING }
            received = deliverer.tryConsume(delivery.scanAttemptId, ScanResultConsumer.BANKING)
        }
        job.join()

        assertEquals("USR_01", received?.cardId)
    }

    @Test
    fun consumerTargetingPreventsCrossDelivery() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.AUCTION)
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(attemptId, card))

        assertNull(deliverer.tryConsume(attemptId, ScanResultConsumer.BANKING))
        assertEquals("USR_01", deliverer.tryConsume(attemptId, ScanResultConsumer.AUCTION)?.cardId)
    }

    @Test
    fun duplicateConsumeAttemptIsRejected() = runTest {
        deliverer.prepareConsumer(ScanResultConsumer.DEBT)
        val card = carCard().copy(cardId = "PRP_01", cardType = CardType.PROPERTY, qrPayload = "MUB:P:01")
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(attemptId, card))

        assertEquals("PRP_01", deliverer.tryConsume(attemptId, ScanResultConsumer.DEBT)?.cardId)
        assertNull(deliverer.tryConsume(attemptId, ScanResultConsumer.DEBT))
    }

    @Test
    fun successfulConsumeClearsPendingScanRequest() {
        deliverer.prepareConsumer(ScanResultConsumer.GAME, ScanRequest.player())
        assertEquals("Scan a Player Card", deliverer.peekScanRequest()?.instruction)
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(attemptId, carCard()))
        deliverer.tryConsume(attemptId, ScanResultConsumer.GAME)
        assertNull(deliverer.peekScanRequest())
    }

    @Test
    fun stagingWithoutPreparedConsumerFailsSafely() {
        val card = carCard()
        val attemptId = deliverer.nextScanAttemptId()
        assertTrue(!deliverer.stageResolvedCard(attemptId, card))
        assertNull(deliverer.peekPendingFor(ScanResultConsumer.GAME))
    }

    @Test
    fun staleAttemptIdIsRejectedAfterSuccessfulConsume() {
        deliverer.prepareConsumer(ScanResultConsumer.GAME)
        val firstAttempt = deliverer.nextScanAttemptId()
        assertTrue(deliverer.stageResolvedCard(firstAttempt, carCard()))
        assertEquals("USR_01", deliverer.tryConsume(firstAttempt, ScanResultConsumer.GAME)?.cardId)

        val staleAttempt = deliverer.nextScanAttemptId()
        assertNull(deliverer.tryConsume(staleAttempt, ScanResultConsumer.GAME))
    }
}
