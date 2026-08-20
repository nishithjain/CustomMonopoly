package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.scanner.CardTypeValidation
import com.boardbanker.app.scanner.ScannerCardFilter
import com.boardbanker.app.scanner.ScannerController
import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import com.boardbanker.core.scanner.ScanProcessorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserCardSoundRegistryTest {
    @Test
    fun mapsAllFourPlayerIds() {
        assertEquals("user_car", UserCardSoundRegistry.soundResourceNameFor("USR_01"))
        assertEquals("user_helicopter", UserCardSoundRegistry.soundResourceNameFor("USR_02"))
        assertEquals("user_ship", UserCardSoundRegistry.soundResourceNameFor("USR_03"))
        assertEquals("user_aeroplane", UserCardSoundRegistry.soundResourceNameFor("USR_04"))
    }

    @Test
    fun errorSoundConstant() {
        assertEquals("error", UserCardSoundRegistry.ERROR_SOUND)
    }
}

class ScanAudioFeedbackTest {
    private lateinit var audio: RecordingGameAudioFeedback
    private lateinit var controller: ScannerController

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        controller = ScannerController(AppTestSupport.definitions)
    }

    @Test
    fun validUserScanPlaysUserSoundOnce() {
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.USER)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertEquals(listOf("USR_01"), audio.userCardCalls)
        assertTrue(audio.errorCalls.isEmpty())
        assertTrue(audio.userThenErrorCalls.isEmpty())
    }

    @Test
    fun allUserIdsMapToUserSoundCalls() {
        val payloads = mapOf(
            "MUB:PL:CAR" to "USR_01",
            "MUB:PL:HELICOPTER" to "USR_02",
            "MUB:PL:SHIP" to "USR_03",
            "MUB:PL:AEROPLANE" to "USR_04",
        )
        payloads.forEach { (payload, playerId) ->
            audio.reset()
            val result = controller.onQrPayload(payload) as ScanProcessorResult.CardResolved
            ScanAudioFeedback.onScanProcessed(
                audio,
                result,
                CardTypeValidation.Accepted,
            )
            assertEquals(listOf(playerId), audio.userCardCalls)
        }
    }

    @Test
    fun unknownQrPlaysErrorOnly() {
        val result = controller.onQrPayload("https://example.com") as ScanProcessorResult.UnknownCard
        ScanAudioFeedback.onScanProcessed(audio, result, validation = null)
        assertTrue(audio.userCardCalls.isEmpty())
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun wrongPropertyWhenPlayerExpectedPlaysErrorOnly() {
        val result = controller.onQrPayload("MUB:P:01") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.USER)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertTrue(audio.userCardCalls.isEmpty())
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun wrongEventWhenPropertyExpectedPlaysErrorOnly() {
        val result = controller.onQrPayload("MUB:E:E01") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.PROPERTY)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertTrue(audio.userCardCalls.isEmpty())
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun wrongUserWhenPropertyExpectedPlaysUserThenError() {
        val result = controller.onQrPayload("MUB:PL:CAR") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.PROPERTY)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertTrue(audio.userCardCalls.isEmpty())
        assertTrue(audio.errorCalls.isEmpty())
        assertEquals(listOf("USR_01"), audio.userThenErrorCalls)
    }

    @Test
    fun validPropertyScanProducesNoAudio() {
        val result = controller.onQrPayload("MUB:P:01") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.PROPERTY)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertTrue(audio.userCardCalls.isEmpty())
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun validEventScanProducesNoAudio() {
        val result = controller.onQrPayload("MUB:E:E01") as ScanProcessorResult.CardResolved
        val validation = ScannerCardFilter.validateCardType(result.resolution, CardType.EVENT)
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertTrue(audio.userCardCalls.isEmpty())
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun duplicateDetectionsProduceSingleUserSound() {
        var userSounds = 0
        repeat(10) {
            val result = controller.onQrPayload("MUB:PL:CAR")
            if (result is ScanProcessorResult.CardResolved) {
                ScanAudioFeedback.onScanProcessed(
                    audio,
                    result,
                    CardTypeValidation.Accepted,
                )
                userSounds += audio.userCardCalls.size
            }
        }
        assertEquals(1, userSounds)
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun duplicateWrongUserScanProducesSingleUserThenError() {
        var sequences = 0
        repeat(10) {
            val result = controller.onQrPayload("MUB:PL:CAR")
            if (result is ScanProcessorResult.CardResolved) {
                ScanAudioFeedback.onScanProcessed(
                    audio,
                    result,
                    CardTypeValidation.WrongType(CardType.PROPERTY, CardType.USER),
                )
                sequences = audio.userThenErrorCalls.size
            }
        }
        assertEquals(1, sequences)
    }

    @Test
    fun ignoredScanProducesNoAudio() {
        controller.onQrPayload("MUB:PL:CAR")
        controller.lockAfterResolved()
        val ignored = controller.onQrPayload("MUB:PL:HELICOPTER")
        ScanAudioFeedback.onScanProcessed(audio, ignored, validation = null)
        assertTrue(audio.userCardCalls.isEmpty())
        assertTrue(audio.errorCalls.isEmpty())
    }
}

class InvalidUserActionAudioTest {
    private val audio = RecordingGameAudioFeedback()

    @Test
    fun duplicatePlayerAttemptPlaysError() {
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            audio,
            com.boardbanker.core.error.GameError.DuplicatePlayer("USR_01"),
        )
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun insufficientFundsDoesNotPlayError() {
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            audio,
            com.boardbanker.core.error.GameError.InsufficientFunds("USR_01", 100, 20),
        )
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun undoNotAllowedPlaysError() {
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            audio,
            com.boardbanker.core.error.GameError.UndoNotAllowed("Nothing to undo"),
        )
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun auctionErrorPlaysError() {
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            audio,
            com.boardbanker.core.error.GameError.AuctionError("Invalid bid"),
        )
        assertEquals(1, audio.errorCalls.size)
    }
}

class GameplayWorkflowAudioTest {
    private val definitions = AppTestSupport.definitions
    private val controller = com.boardbanker.app.gameplay.workflow.GameplayWorkflowController(definitions)
    private val audio = RecordingGameAudioFeedback()

    @Test
    fun workflowWrongUserBeforeBuyDecisionPlaysErrorInWorkflowLayer() {
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        val actions = controller.onUserScanned("USR_01", session)
        val wrong = actions.filterIsInstance<com.boardbanker.app.gameplay.workflow.WorkflowAction.WrongCardType>()
        assertEquals(1, wrong.size)
        wrong.forEach { InvalidUserActionAudio.notifyInvalidUserAction(audio) }
        assertEquals(1, audio.errorCalls.size)
    }

    @Test
    fun invalidEventTargetPlaysErrorInWorkflowLayer() {
        controller.onEventScanned("EVT_06")
        controller.onEventContinue()
        val actions = controller.onEventPropertyScanned("PRP_01")
        val wrong = actions.filterIsInstance<com.boardbanker.app.gameplay.workflow.WorkflowAction.WrongCardType>()
        assertEquals(1, wrong.size)
        wrong.forEach { InvalidUserActionAudio.notifyInvalidUserAction(audio) }
        assertEquals(1, audio.errorCalls.size)
    }
}
