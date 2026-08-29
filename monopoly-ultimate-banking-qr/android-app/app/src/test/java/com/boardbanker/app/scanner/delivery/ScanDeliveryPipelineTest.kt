package com.boardbanker.app.scanner.delivery

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.audio.ScanAudioFeedback
import com.boardbanker.app.scanner.CardTypeValidation
import com.boardbanker.app.scanner.ScannerCardFilter
import com.boardbanker.app.scanner.ScannerController
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import com.boardbanker.core.scanner.ScanProcessorResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the scanner-to-workflow boundary without CameraX or navigation.
 */
class ScanDeliveryPipelineTest {
    private lateinit var deliverer: ScanResultDeliverer
    private lateinit var controller: ScannerController
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        deliverer = ScanResultDeliverer()
        controller = ScannerController(AppTestSupport.definitions)
        audio = RecordingGameAudioFeedback()
    }

    private fun resolveAcceptedUser(
        payload: String = "MUB:PL:CAR",
        expectedCardType: CardType? = CardType.USER,
        consumer: ScanResultConsumer = ScanResultConsumer.PLAYER_SETUP,
    ): Pair<Long, ResolvedCard> {
        deliverer.prepareConsumer(consumer)
        val scanAttemptId = deliverer.nextScanAttemptId()
        val result = controller.onQrPayload(payload)
        check(result is ScanProcessorResult.CardResolved)
        val validation = ScannerCardFilter.validateCardType(result.resolution, expectedCardType)
        check(validation == CardTypeValidation.Accepted)
        val resolved = result.resolution.toResolvedCard()
        controller.lockAfterResolved()
        deliverer.stageResolvedCard(scanAttemptId, resolved)
        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)
        return scanAttemptId to resolved
    }

    private fun consume(attemptId: Long, consumer: ScanResultConsumer): ResolvedCard? =
        deliverer.tryConsume(attemptId, consumer)

    @Test
    fun singleUserScan_deliversOnce_playsAudioOnce() {
        val scanAttemptId = deliverer.nextScanAttemptId()
        deliverer.prepareConsumer(ScanResultConsumer.PLAYER_SETUP)
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.USER)
        val resolved = result.resolution.toResolvedCard()
        controller.lockAfterResolved()
        deliverer.stageResolvedCard(scanAttemptId, resolved)
        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)

        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.PLAYER_SETUP)?.cardId)
        assertEquals(listOf("USR_01"), audio.userCardCalls)
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun userCardDoesNotRequireSecondPhysicalScan() {
        val (scanAttemptId, resolved) = resolveAcceptedUser(consumer = ScanResultConsumer.GAME)
        assertEquals("USR_01", resolved.cardId)
        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.GAME)?.cardId)
        assertNull(consume(scanAttemptId, ScanResultConsumer.GAME))
    }

    @Test
    fun repeatedCameraFrames_acceptOnce_deliverOnce_playAudioOnce() {
        val scanAttemptId = deliverer.nextScanAttemptId()
        deliverer.prepareConsumer(ScanResultConsumer.GAME)
        val first = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(first.resolution, CardType.USER)
        val resolved = first.resolution.toResolvedCard()
        controller.lockAfterResolved()
        deliverer.stageResolvedCard(scanAttemptId, resolved)
        ScanAudioFeedback.onScanProcessed(audio, first, validation, scanAttemptId)

        repeat(3) {
            val ignored = controller.onQrPayload("MUB:PL:CAR")
            assertTrue(ignored is ScanProcessorResult.Ignored)
        }

        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.GAME)?.cardId)
        assertEquals(1, audio.userCardCalls.size)
    }

    @Test
    fun navigationTiming_callerReceivesAfterSimulatedPop() = runTest {
        val (scanAttemptId, resolved) = resolveAcceptedUser(consumer = ScanResultConsumer.GAME)
        assertEquals("USR_01", resolved.cardId)

        val pending = deliverer.peekPendingFor(ScanResultConsumer.GAME)
        assertEquals("USR_01", pending?.card?.cardId)

        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.GAME)?.cardId)
    }

    @Test
    fun slowCaller_pendingSurvivesUntilConsumed() {
        val (scanAttemptId, _) = resolveAcceptedUser(consumer = ScanResultConsumer.BANKING)
        assertEquals("USR_01", deliverer.peekPendingFor(ScanResultConsumer.BANKING)?.card?.cardId)
        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.BANKING)?.cardId)
    }

    @Test
    fun wrongTypeAtScannerFilter_isNotDelivered_playsUserThenError() {
        deliverer.prepareConsumer(ScanResultConsumer.BANKING)
        val scanAttemptId = deliverer.nextScanAttemptId()
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.PROPERTY)
        controller.lockAfterResolved()
        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)

        assertNull(deliverer.peekPendingFor(ScanResultConsumer.BANKING))
        assertEquals(listOf("USR_01"), audio.userThenErrorCalls)
    }

    @Test
    fun wrongTypeWorkflowDelivery_anyScannerFilter_deliversToWorkflow() {
        deliverer.prepareConsumer(ScanResultConsumer.GAME)
        val scanAttemptId = deliverer.nextScanAttemptId()
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, expectedCardType = null)
        val resolved = result.resolution.toResolvedCard()
        controller.lockAfterResolved()
        deliverer.stageResolvedCard(scanAttemptId, resolved)
        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)

        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.GAME)?.cardId)
        assertEquals(listOf("USR_01"), audio.userCardCalls)
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun audioFailureDoesNotBlockScanDelivery() {
        val failingAudio = ThrowingGameAudioFeedback()
        deliverer.prepareConsumer(ScanResultConsumer.PLAYER_SETUP)
        val scanAttemptId = deliverer.nextScanAttemptId()
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = CardTypeValidation.Accepted
        val resolved = result.resolution.toResolvedCard()
        controller.lockAfterResolved()
        deliverer.stageResolvedCard(scanAttemptId, resolved)
        ScanAudioFeedback.onScanProcessed(failingAudio, result, validation, scanAttemptId)

        assertEquals("USR_01", consume(scanAttemptId, ScanResultConsumer.PLAYER_SETUP)?.cardId)
    }

    private fun com.boardbanker.core.card.CardResolution.Success.toResolvedCard() = ResolvedCard(
        cardId = cardId,
        cardType = cardType,
        displayName = displayName,
        qrPayload = qrPayload,
    )
}

private class ThrowingGameAudioFeedback : GameAudioFeedback {
    override var enabled: Boolean = true

    override fun playUserCard(playerId: String) {
        error("SoundPool failure")
    }

    override fun playError() {
        error("SoundPool failure")
    }

    override fun playUserCardThenError(playerId: String) {
        error("SoundPool failure")
    }

    override fun playScanPrompt() = Unit
    override fun playGameStarted() = Unit
    override fun playPropertyPurchased() = Unit
    override fun playColorSetComplete() = Unit
    override fun playRentTransfer() = Unit
    override fun playRentLevelIncreased() = Unit
    override fun playRentLevelDecreased() = Unit
    override fun playGo() = Unit
    override fun playGoToJail() = Unit
    override fun playJail() = Unit
    override fun playAuctionBegins() = Unit
    override fun playAuctionEnding() = Unit
    override fun playKaChing() = Unit
    override fun playMoneyLost() = Unit
    override fun playUndo() = Unit
    override fun playUndoLastAction() = Unit
    override fun playLostGame() = Unit
    override fun playWinner() = Unit

    override fun release() = Unit
}
