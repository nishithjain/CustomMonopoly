package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DiceGambleMode
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 4 Lucky Break, movement, Lucky Draw, and turn-transition sound verification.
 */
class Batch4SoundTests {
    private val ukEngine = AppTestSupport.engine
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private lateinit var indiaEngine: DefaultGameEngine
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        indiaEngine = DefaultGameEngine(indiaDefinitions, SequenceDiceRoller(4 to 4))
        ScanPromptAudio.resetForTests()
    }

    private fun indiaSession() = AppTestSupport.newGameForEdition(EditionIds.INDIA)

    private fun startLuckyBreakInApp(
        engine: DefaultGameEngine,
        session: com.boardbanker.core.model.GameSession,
        playerId: String = "USR_01",
    ): com.boardbanker.core.model.GameSession {
        var current = engine.process(session, GameCommand.ApplyEvent("EVT_17", playerId)).session
        return AppTestSupport.selectDiceGambleMode(current, DiceGambleMode.IN_APP, engine)
    }

    private fun playWorkflow(
        result: com.boardbanker.core.engine.GameResult,
        before: com.boardbanker.core.model.GameSession,
        context: WorkflowCommandContext,
    ) {
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(context),
        )
    }

    @Test
    fun acceptedLuckyBreakRollPlaysDiceRollOnce() {
        var session = startLuckyBreakInApp(indiaEngine, indiaSession())
        val before = session
        val result = indiaEngine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        val cues = GameplayOutcomeAudio.resolveLuckyBreakRollCues(result)
        assertEquals(listOf(GameplayAudioCue.DICE_ROLL, GameplayAudioCue.BANK_CREDIT), cues)
        playWorkflow(result, before, WorkflowCommandContext.RollEventDice("EVT_17"))
        assertEquals(listOf("DICE_ROLL", "BANK_CREDIT"), audio.gameplayCalls)
    }

    @Test
    fun intermediateLuckyBreakRollPlaysOnlyDiceRoll() {
        val engine = DefaultGameEngine(indiaDefinitions, SequenceDiceRoller(3 to 5))
        var session = startLuckyBreakInApp(engine, indiaSession())
        val before = session
        val result = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(listOf(GameplayAudioCue.DICE_ROLL), GameplayOutcomeAudio.resolveLuckyBreakRollCues(result))
        playWorkflow(result, before, WorkflowCommandContext.RollEventDice("EVT_17"))
        assertEquals(listOf("DICE_ROLL"), audio.gameplayCalls)
    }

    @Test
    fun failedLuckyBreakRollPlaysNothing() {
        val engine = DefaultGameEngine(
            indiaDefinitions,
            SequenceDiceRoller(3 to 5, 2 to 4, 1 to 6),
        )
        var session = startLuckyBreakInApp(engine, indiaSession())
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val completed = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        val duplicate = engine.process(completed.session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertFalse(duplicate.isSuccess)
        assertTrue(GameplayOutcomeAudio.resolveLuckyBreakRollCues(duplicate).isEmpty())
    }

    @Test
    fun finalFailedLuckyBreakRollSequencesDiceRollThenBankDebit() {
        val engine = DefaultGameEngine(
            indiaDefinitions,
            SequenceDiceRoller(3 to 5, 2 to 4, 1 to 6),
        )
        var session = startLuckyBreakInApp(engine, indiaSession())
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        val before = session
        val result = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01"))
        assertEquals(
            listOf(GameplayAudioCue.DICE_ROLL, GameplayAudioCue.BANK_DEBIT),
            GameplayOutcomeAudio.resolveLuckyBreakRollCues(result),
        )
        playWorkflow(result, before, WorkflowCommandContext.RollEventDice("EVT_17"))
        assertEquals(listOf("DICE_ROLL", "BANK_DEBIT"), audio.gameplayCalls)
    }

    @Test
    fun evt02PlaysMovePlayerOnce() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_02", "USR_01"))
        assertTrue(GameplayOutcomeAudio.isPlayerMovement(result))
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_02")),
        )
        assertEquals(listOf(GameplayAudioCue.MOVE_PLAYER), cues)
        playWorkflow(result, before, WorkflowCommandContext.ApplyEvent("EVT_02"))
        assertEquals(listOf("MOVE_PLAYER"), audio.gameplayCalls)
    }

    @Test
    fun evt21PlaysMovePlayerOnce() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(
            session,
            GameCommand.ApplyEvent("EVT_21", "USR_01", fromBoardPosition = 28),
        )
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_21")),
        )
        assertEquals(listOf(GameplayAudioCue.MOVE_PLAYER), cues)
    }

    @Test
    fun evt21PassingGoSequencesMovePlayerThenGo() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(
            session,
            GameCommand.ApplyEvent("EVT_21", "USR_01", fromBoardPosition = 30),
        )
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_21")),
        )
        assertEquals(listOf(GameplayAudioCue.MOVE_PLAYER, GameplayAudioCue.GO), cues)
        GameplayOutcomeAudio.playCues(audio, cues)
        assertEquals(listOf("MOVE_PLAYER", "GO"), audio.gameplayCalls)
    }

    @Test
    fun evt15PlaysLuckyDrawNotEventApplied() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01"))
        assertTrue(GameplayOutcomeAudio.luckyDrawCreated(before, result))
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertEquals(listOf(GameplayAudioCue.LUCKY_DRAW), cues)
        playWorkflow(result, before, WorkflowCommandContext.ApplyEvent("EVT_15"))
        assertEquals(listOf("LUCKY_DRAW"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("EVENT_APPLIED"))
    }

    @Test
    fun resolvingLuckyDrawFollowUpDoesNotReplayLuckyDraw() {
        var session = indiaSession()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.ResolvePendingEventDraw("EVT_03", "USR_01"))
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ResolvePendingEventDraw("EVT_03")),
        )
        assertFalse(cues.contains(GameplayAudioCue.LUCKY_DRAW))
    }

    @Test
    fun evt18ApplicationDoesNotPlayTurnSkipped() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_01"))
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_18")),
        )
        assertFalse(cues.contains(GameplayAudioCue.TURN_SKIPPED))
    }

    @Test
    fun actualSkippedTurnPlaysTurnSkipped() {
        var session = indiaSession()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_02")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameplayAudioCue.TURN_SKIPPED, GameplayOutcomeAudio.resolveTurnChangeCue(result))
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.Banking(GameCommand.EndTurn("USR_01")),
        )
        assertEquals(listOf("TURN_SKIPPED"), audio.gameplayCalls)
    }

    @Test
    fun skippedTurnDoesNotAlsoPlayTurnChanged() {
        var session = indiaSession()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_18", "USR_02")).session
        val result = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertNull(
            if (GameplayOutcomeAudio.resolveTurnChangeCue(result) == GameplayAudioCue.TURN_CHANGED) {
                GameplayAudioCue.TURN_CHANGED
            } else {
                null
            },
        )
        assertEquals(GameplayAudioCue.TURN_SKIPPED, GameplayOutcomeAudio.resolveTurnChangeCue(result))
    }

    @Test
    fun evt24ApplicationDoesNotPlayExtraTurn() {
        val session = indiaSession()
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01"))
        val cues = GameplayOutcomeAudio.resolveCues(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_24")),
        )
        assertEquals(listOf(GameplayAudioCue.EVENT_APPLIED), cues)
        assertFalse(cues.contains(GameplayAudioCue.EXTRA_TURN))
    }

    @Test
    fun actualExtraTurnPlaysExtraTurn() {
        var session = indiaSession()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameplayAudioCue.EXTRA_TURN, GameplayOutcomeAudio.resolveTurnChangeCue(result))
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.Banking(GameCommand.EndTurn("USR_01")),
        )
        assertEquals(listOf("EXTRA_TURN"), audio.gameplayCalls)
    }

    @Test
    fun extraTurnDoesNotAlsoPlayTurnChanged() {
        var session = indiaSession()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01")).session
        val result = indiaEngine.process(session, GameCommand.EndTurn("USR_01"))
        assertEquals(GameplayAudioCue.EXTRA_TURN, GameplayOutcomeAudio.resolveTurnChangeCue(result))
        assertFalse(GameplayOutcomeAudio.resolveTurnChangeCue(result) == GameplayAudioCue.TURN_CHANGED)
    }

    @Test
    fun resumeAndFailedCommandsDoNotReplayBatch4Sounds() {
        val emptySession = indiaSession()
        audio.reset()
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            com.boardbanker.core.engine.GameResult(session = emptySession),
            emptySession,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertTrue(audio.gameplayCalls.isEmpty())

        var luckyDrawSession = indiaSession()
        luckyDrawSession = indiaEngine.process(luckyDrawSession, GameCommand.ApplyEvent("EVT_15", "USR_01")).session
        val rejected = indiaEngine.process(luckyDrawSession, GameCommand.ResolvePendingEventDraw("EVT_15", "USR_01"))
        assertEquals(GameOutcome.REJECTED, rejected.outcome)
        assertTrue(
            GameplayOutcomeAudio.resolveCues(
                rejected,
                luckyDrawSession,
                CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ResolvePendingEventDraw("EVT_15")),
            ).isEmpty(),
        )
    }

    @Test
    fun batch4SoundRegistryMapsAllIdentifiers() {
        assertEquals("dice_roll", GameSoundRegistry.resourceNameFor(GameSound.DICE_ROLL))
        assertEquals("move_player", GameSoundRegistry.resourceNameFor(GameSound.MOVE_PLAYER))
        assertEquals("lucky_draw", GameSoundRegistry.resourceNameFor(GameSound.LUCKY_DRAW))
        assertEquals("turn_skipped", GameSoundRegistry.resourceNameFor(GameSound.TURN_SKIPPED))
        assertEquals("extra_turn", GameSoundRegistry.resourceNameFor(GameSound.EXTRA_TURN))
    }
}
