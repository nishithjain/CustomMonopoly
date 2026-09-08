package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 3 bank, money-transfer, GO, Location, and Jail sound verification.
 */
class Batch3SoundTests {
    private val ukEngine = AppTestSupport.engine
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        ScanPromptAudio.resetForTests()
    }

    private fun playBanking(
        result: com.boardbanker.core.engine.GameResult,
        before: com.boardbanker.core.model.GameSession,
        command: GameCommand,
    ) {
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.Banking(command),
        )
    }

    private fun playEvent(
        result: com.boardbanker.core.engine.GameResult,
        before: com.boardbanker.core.model.GameSession,
        eventId: String,
    ) {
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent(eventId)),
        )
    }

    @Test
    fun bankCreditPlaysBankCreditSound() {
        val session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_03", "USR_01"))
        assertEquals(
            GameplayAudioCue.BANK_CREDIT,
            GameplayOutcomeAudio.resolvePrimaryOutcomeCue(result, before),
        )
        playEvent(result, before, "EVT_03")
        assertEquals(listOf("BANK_CREDIT"), audio.gameplayCalls)
    }

    @Test
    fun bankDebitPlaysBankDebitSound() {
        val session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val before = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(balance = 50000)
            ),
        )
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_05", "USR_01"))
        assertEquals(
            GameplayAudioCue.BANK_DEBIT,
            GameplayOutcomeAudio.resolvePrimaryOutcomeCue(result, before),
        )
        playEvent(result, before, "EVT_05")
        assertEquals(listOf("BANK_DEBIT"), audio.gameplayCalls)
    }

    @Test
    fun festivalContributionPlaysMoneyTransferOnce() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02", "USR_03"))
        session = session.copy(
            players = session.players.mapValues { (_, player) -> player.copy(balance = 50000) },
        )
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))
        assertTrue(GameplayOutcomeAudio.hasNonRentPlayerTransfer(result))
        assertEquals(
            GameplayAudioCue.MONEY_TRANSFER,
            GameplayOutcomeAudio.resolvePrimaryOutcomeCue(result, before),
        )
        playEvent(result, before, "EVT_06")
        assertEquals(listOf("MONEY_TRANSFER"), audio.gameplayCalls)
    }

    @Test
    fun birthdayCelebrationPlaysMoneyTransferOnce() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02", "USR_03"))
        session = session.copy(
            players = session.players.mapValues { (_, player) -> player.copy(balance = 50000) },
        )
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_01"))
        assertEquals(
            GameplayAudioCue.MONEY_TRANSFER,
            GameplayOutcomeAudio.resolvePrimaryOutcomeCue(result, before),
        )
        playEvent(result, before, "EVT_07")
        assertEquals(listOf("MONEY_TRANSFER"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("RENT_TRANSFER"))
    }

    @Test
    fun rentPaymentStillUsesRentTransfer() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        val before = session
        val result = ukEngine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"))
        val context = WorkflowCommandContext.PropertyLanding("USR_02", "PRP_01")
        assertEquals(
            GameplayAudioCue.RENT_TRANSFER,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
    }

    @Test
    fun goCollectionPlaysGoNotBankCredit() {
        val session = AppTestSupport.newGame()
        val before = session
        val command = GameCommand.PayGoSalary("USR_01")
        val result = ukEngine.process(session, command)
        assertEquals(
            GameplayAudioCue.GO,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)),
        )
        playBanking(result, before, command)
        assertEquals(listOf("GO"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("BANK_CREDIT"))
    }

    @Test
    fun suppressedGoCollectionPlaysNoGoSound() {
        val session = AppTestSupport.newGame()
        val before = session
        val command = GameCommand.PayLocationFee("USR_01", "PRP_10")
        val result = ukEngine.process(session, command)
        val cue = GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command))
        assertEquals(GameplayAudioCue.LOCATION, cue)
        assertFalse(cue == GameplayAudioCue.GO)
        assertFalse(result.transactions.any {
            it.transactionType == TransactionType.BANK_CREDIT && it.amount == 200
        })
    }

    @Test
    fun locationSuccessPlaysLocationSound() {
        val session = AppTestSupport.newGame()
        val before = session
        val command = GameCommand.PayLocationFee("USR_01", "PRP_10")
        val result = ukEngine.process(session, command)
        assertEquals(
            GameplayAudioCue.LOCATION,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)),
        )
        playBanking(result, before, command)
        assertEquals(listOf("LOCATION"), audio.gameplayCalls)
    }

    @Test
    fun locationFeeDoesNotAlsoPlayGenericDebitSound() {
        val session = AppTestSupport.newGame()
        val before = session
        val command = GameCommand.PayLocationFee("USR_01", "PRP_10")
        val result = ukEngine.process(session, command)
        playBanking(result, before, command)
        assertEquals(listOf("LOCATION"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("BANK_DEBIT"))
    }

    @Test
    fun goToJailPlaysGoToJailSound() {
        val session = AppTestSupport.newGame()
        val before = session
        val command = GameCommand.SendPlayerToJail("USR_01")
        val result = ukEngine.process(session, command)
        assertEquals(
            GameplayAudioCue.GO_TO_JAIL,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)),
        )
        playBanking(result, before, command)
        assertEquals(listOf("GO_TO_JAIL"), audio.gameplayCalls)
    }

    @Test
    fun normalJailReleasePlaysJailReleaseSound() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val before = session
        val command = GameCommand.PayJailFee("USR_01")
        val result = ukEngine.process(session, command)
        assertEquals(
            GameplayAudioCue.JAIL_RELEASE,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)),
        )
        playBanking(result, before, command)
        assertEquals(listOf("JAIL_RELEASE"), audio.gameplayCalls)
    }

    @Test
    fun jailFeeDoesNotAlsoPlayGenericDebitSound() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val before = session
        val command = GameCommand.PayJailFee("USR_01")
        val result = ukEngine.process(session, command)
        playBanking(result, before, command)
        assertEquals(listOf("JAIL_RELEASE"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("BANK_DEBIT"))
    }

    @Test
    fun receivingJailPassPlaysJailPassSound() {
        val session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01"))
        assertTrue(GameplayOutcomeAudio.jailPassGranted(before, result))
        assertEquals(
            GameplayAudioCue.JAIL_PASS,
            GameplayOutcomeAudio.resolvePrimaryOutcomeCue(result, before),
        )
        playEvent(result, before, "EVT_11")
        assertEquals(listOf("JAIL_PASS"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("EVENT_APPLIED"))
    }

    @Test
    fun usingJailPassPlaysJailPassAgain() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01")).session
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val before = session
        val command = GameCommand.UseGetOutOfJailPass("USR_01")
        val result = indiaEngine.process(session, command)
        assertEquals(
            GameplayAudioCue.JAIL_PASS,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)),
        )
        playBanking(result, before, command)
        assertEquals(listOf("JAIL_PASS"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("JAIL_RELEASE"))
    }

    @Test
    fun invalidJailPassScanPlaysNoJailPassSound() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.GetOutOfJailWithPass("USR_01", "EVT_01"))
        assertFalse(result.isSuccess)
        val command = GameCommand.GetOutOfJailWithPass("USR_01", "EVT_01")
        assertNull(GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.Banking(command)))
        playBanking(result, before, command)
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun resumeAndFailedCommandsDoNotReplayBatch3Sounds() {
        val session = AppTestSupport.newGame()
        audio.reset()
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            com.boardbanker.core.engine.GameResult(session = session),
            session,
            CommitAudioTrigger.Banking(GameCommand.PayGoSalary("USR_01")),
        )
        assertTrue(audio.gameplayCalls.isEmpty())

        val failed = ukEngine.process(session, GameCommand.PayGoSalary("USR_01"))
        assertNull(
            GameplayOutcomeAudio.resolveCue(
                failed.copy(error = com.boardbanker.core.error.GameError.Validation("fail")),
                session,
                CommitAudioTrigger.Banking(GameCommand.PayGoSalary("USR_01")),
            ),
        )
    }

    @Test
    fun batch3SoundRegistryMapsAllIdentifiers() {
        assertEquals("go", GameSoundRegistry.resourceNameFor(GameSound.GO))
        assertEquals("location", GameSoundRegistry.resourceNameFor(GameSound.LOCATION))
        assertEquals("go_to_jail", GameSoundRegistry.resourceNameFor(GameSound.GO_TO_JAIL))
        assertEquals("jail", GameSoundRegistry.resourceNameFor(GameSound.JAIL_RELEASE))
        assertEquals("jail_pass", GameSoundRegistry.resourceNameFor(GameSound.JAIL_PASS))
        assertEquals("ka_ching", GameSoundRegistry.resourceNameFor(GameSound.BANK_CREDIT))
        assertEquals("someone_just_took_your_money", GameSoundRegistry.resourceNameFor(GameSound.BANK_DEBIT))
        assertEquals("money_transfer", GameSoundRegistry.resourceNameFor(GameSound.MONEY_TRANSFER))
    }
}
