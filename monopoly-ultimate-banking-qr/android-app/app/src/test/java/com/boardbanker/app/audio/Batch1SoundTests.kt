package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 1 common sound framework verification.
 */
class Batch1SoundTests {
    private val engine = AppTestSupport.engine
    private val definitions = AppTestSupport.definitions
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        ScanPromptAudio.resetForTests()
    }

    @Test
    fun successfulQrScanPlaysScanCardOnce() {
        val controller = com.boardbanker.app.scanner.ScannerController(definitions)
        val result = controller.onQrPayload("MUB:P:01") as com.boardbanker.core.scanner.ScanProcessorResult.CardResolved
        val validation = com.boardbanker.app.scanner.ScannerCardFilter.validateCardType(
            result.resolution,
            com.boardbanker.core.card.CardType.PROPERTY,
        )
        ScanAudioFeedback.onScanProcessed(audio, result, validation)
        assertEquals(listOf("SCAN_CARD"), audio.gameplayCalls)
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun invalidQrScanPlaysErrorNotScanCard() {
        val controller = com.boardbanker.app.scanner.ScannerController(definitions)
        val result = controller.onQrPayload("https://example.com")
            as com.boardbanker.core.scanner.ScanProcessorResult.UnknownCard
        ScanAudioFeedback.onScanProcessed(audio, result, validation = null)
        assertEquals(1, audio.errorCalls.size)
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun startGamePlaysGameStartsOnce() {
        var session = engine.process(
            com.boardbanker.core.model.GameSession(
                gameId = "BATCH1",
                editionId = definitions.editionId,
            ),
            GameCommand.CreateGame("BATCH1"),
        ).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Aditya")).session
        val before = session
        val result = engine.process(session, GameCommand.StartGame)
        GameplayOutcomeAudio.playCommittedOutcome(audio, result, before, CommitAudioTrigger.GameStarted)
        assertEquals(listOf("GAME_STARTS"), audio.gameplayCalls)
    }

    @Test
    fun resumeGameDoesNotReplayGameStarts() {
        val session = AppTestSupport.newGame()
        audio.reset()
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            com.boardbanker.core.engine.GameResult(session = session),
            session,
            CommitAudioTrigger.GameStarted,
        )
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun ordinaryTurnChangePlaysTurnChangedOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.EndTurn("USR_01"))
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.Banking(GameCommand.EndTurn("USR_01")),
        )
        assertEquals(GameplayAudioCue.TURN_CHANGED, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.Banking(GameCommand.EndTurn("USR_01")),
        )
        assertEquals(listOf("TURN_CHANGED"), audio.gameplayCalls)
    }

    @Test
    fun extraTurnStartDoesNotPlayTurnChanged() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val indiaEngine = com.boardbanker.core.engine.DefaultGameEngine(
            AppTestSupport.editionRepository.load(EditionIds.INDIA),
        )
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameplayAudioCue.EXTRA_TURN, GameplayOutcomeAudio.resolveTurnChangeCue(result))
    }

    @Test
    fun genericEventPlaysEventAppliedFallback() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_15", "USR_01", propertyId = "PRP_03"),
        )
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertEquals(GameplayAudioCue.EVENT_APPLIED, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertEquals(listOf("EVENT_APPLIED"), audio.gameplayCalls)
    }

    @Test
    fun specificEventSoundDoesNotAlsoPlayEventAppliedFallback() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01", targetPlayerId = "USR_02"))
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_11")),
        )
        assertEquals(GameplayAudioCue.BANK_CREDIT, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_11")),
        )
        assertEquals(listOf("BANK_CREDIT"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("EVENT_APPLIED"))
    }

    @Test
    fun scanPromptTokenPreventsDuplicatePlayback() {
        val token = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(audio, token)
        ScanPromptAudio.playOnce(audio, token)
        assertEquals(listOf("SCAN_CARD"), audio.gameplayCalls)
    }

    @Test
    fun gameSoundRegistryMapsBatch1Sounds() {
        assertEquals("scan_card", GameSoundRegistry.resourceNameFor(GameSound.SCAN_CARD))
        assertEquals("error", GameSoundRegistry.resourceNameFor(GameSound.ERROR))
        assertEquals("game_starts", GameSoundRegistry.resourceNameFor(GameSound.GAME_STARTS))
        assertEquals("turn_changed", GameSoundRegistry.resourceNameFor(GameSound.TURN_CHANGED))
        assertEquals("event_applied", GameSoundRegistry.resourceNameFor(GameSound.EVENT_APPLIED))
        assertEquals("undo_last_action", GameSoundRegistry.resourceNameFor(GameSound.UNDO_LAST_ACTION))
        assertEquals("undo", GameSoundRegistry.resourceNameFor(GameSound.UNDO))
    }
}
