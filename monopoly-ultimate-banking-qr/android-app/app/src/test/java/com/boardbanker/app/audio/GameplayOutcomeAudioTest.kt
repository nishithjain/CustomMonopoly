package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GameplayOutcomeAudioTest {
    private val engine = AppTestSupport.engine
    private val definitions = AppTestSupport.definitions
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        ScanPromptAudio.resetForTests()
    }

    private fun cue(
        result: com.boardbanker.core.engine.GameResult,
        sessionBefore: com.boardbanker.core.model.GameSession,
        trigger: CommitAudioTrigger,
    ): GameplayAudioCue? = GameplayOutcomeAudio.resolveCue(result, sessionBefore, trigger)

    @Test
    fun startGameSuccess_playsGameStartsOnce() {
        var session = engine.process(
            com.boardbanker.core.model.GameSession(gameId = "AUDIO_TEST"),
            GameCommand.CreateGame("AUDIO_TEST"),
        ).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_01", "Nishith")).session
        session = engine.process(session, GameCommand.RegisterPlayer("USR_02", "Aditya")).session
        val before = session
        val result = engine.process(session, GameCommand.StartGame)
        assertEquals(GameplayAudioCue.GAME_STARTS, cue(result, before, CommitAudioTrigger.GameStarted))
        GameplayOutcomeAudio.playCommittedOutcome(audio, result, before, CommitAudioTrigger.GameStarted)
        assertEquals(listOf("GAME_STARTS"), audio.gameplayCalls)
    }

    @Test
    fun propertyPurchaseSuccess_playsPropertyPurchasedOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_01", 1500)
        assertEquals(GameplayAudioCue.PROPERTY_PURCHASED, cue(result, before, CommitAudioTrigger.GameWorkflow(context)))
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(context),
        )
        assertEquals(listOf("PROPERTY_PURCHASED"), audio.gameplayCalls)
    }

    @Test
    fun colorSetCompletion_playsColorSetComplete_notPropertyPurchased() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_03")).session
        val before = session
        val result = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_02"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_02", before.players["USR_01"]!!.balance)
        assertEquals(GameplayAudioCue.COLOR_SET_COMPLETE, cue(result, before, CommitAudioTrigger.GameWorkflow(context)))
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(context),
        )
        assertEquals(listOf("COLOR_SET_COMPLETE"), audio.gameplayCalls)
    }

    @Test
    fun rentTransfer_playsRentTransfer_notRentLevelIncreased() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"))
        val context = WorkflowCommandContext.PropertyLanding("USR_02", "PRP_01")
        assertEquals(GameplayAudioCue.RENT_TRANSFER, cue(result, before, CommitAudioTrigger.GameWorkflow(context)))
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(context),
        )
        assertEquals(listOf("RENT_TRANSFER"), audio.gameplayCalls)
    }

    @Test
    fun ownerLandingRentIncrease_playsRentLevelIncreasedOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.PropertyLanding("USR_01", "PRP_01")
        assertEquals(GameplayAudioCue.RENT_LEVEL_INCREASED, cue(result, before, CommitAudioTrigger.GameWorkflow(context)))
    }

    @Test
    fun ownerAtMaxRentLevel_playsNoRentLevelIncreased() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(currentRentLevel = 5)
            ),
        )
        val before = session
        val result = engine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.PropertyLanding("USR_01", "PRP_01")
        assertNull(cue(result, before, CommitAudioTrigger.GameWorkflow(context)))
    }

    @Test
    fun goSuccess_playsGoOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.PayGoSalary("USR_01"))
        assertEquals(GameplayAudioCue.GO, cue(result, before, CommitAudioTrigger.Banking(GameCommand.PayGoSalary("USR_01"))))
    }

    @Test
    fun evt14_playsGoToJailOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_14", "USR_01", targetPlayerId = "USR_02"))
        assertEquals(
            GameplayAudioCue.GO_TO_JAIL,
            cue(result, before, CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_14"))),
        )
    }

    @Test
    fun evt11_playsKaChingOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01", targetPlayerId = "USR_02"))
        assertEquals(
            GameplayAudioCue.KA_CHING,
            cue(result, before, CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_11"))),
        )
    }

    @Test
    fun evt07_playsMoneyLostOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_01"))
        assertEquals(
            GameplayAudioCue.MONEY_LOST,
            cue(result, before, CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_07"))),
        )
    }

    @Test
    fun undoSuccess_playsUndoOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = engine.process(session, GameCommand.UndoLastAction)
        assertEquals(
            GameplayAudioCue.UNDO_LAST_ACTION,
            cue(result, before, CommitAudioTrigger.Banking(GameCommand.UndoLastAction)),
        )
    }

    @Test
    fun scanPrompt_playsOncePerToken() {
        val token = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(audio, token)
        ScanPromptAudio.playOnce(audio, token)
        assertEquals(listOf("SCAN_CARD"), audio.gameplayCalls)
    }

    @Test
    fun failedCommand_playsNoSuccessAudio() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        assertNull(cue(result.copy(error = com.boardbanker.core.error.GameError.Validation("fail")), before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.Purchase("USR_01", "PRP_01", 1500))))
    }
}

class GameEndAudioCoordinatorTest {
    @Test
    fun winnerPlaysOnlyAfterFreshBankruptcyMark() {
        val audio = RecordingGameAudioFeedback()
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onWinnerScreenPresented(audio)
        assertEquals(emptyList<String>(), audio.gameplayCalls)
        coordinator.markFreshGameEndFromBankruptcy()
        coordinator.onWinnerScreenPresented(audio)
        assertEquals(listOf("WINNER"), audio.gameplayCalls)
    }
}
