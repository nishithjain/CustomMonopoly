package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 2 property, energy grid, rent, and ownership sound verification.
 */
class Batch2SoundTests {
    private val ukEngine = AppTestSupport.engine
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        ScanPromptAudio.resetForTests()
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
    fun successfulPropertyPurchasePlaysPropertyPurchased() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_01", before.players["USR_01"]!!.balance)
        assertEquals(
            GameplayAudioCue.PROPERTY_PURCHASED,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
        playWorkflow(result, before, context)
        assertEquals(listOf("PROPERTY_PURCHASED"), audio.gameplayCalls)
    }

    @Test
    fun failedOrCancelledPurchasePlaysNoPurchaseSound() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val duplicate = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_01", session.players["USR_01"]!!.balance)
        assertNull(GameplayOutcomeAudio.resolveCue(duplicate, session, CommitAudioTrigger.GameWorkflow(context)))
        playWorkflow(duplicate, session, context)
        assertTrue(audio.gameplayCalls.isEmpty())

        var brokeSession = AppTestSupport.newGame()
        brokeSession = brokeSession.copy(
            players = brokeSession.players + (
                "USR_01" to brokeSession.players["USR_01"]!!.copy(balance = 0)
            ),
        )
        val failed = ukEngine.process(brokeSession, GameCommand.PurchaseProperty("USR_01", "PRP_02"))
        assertNull(GameplayOutcomeAudio.resolvePrimaryAssetCue(failed, brokeSession))
        playWorkflow(
            failed,
            brokeSession,
            WorkflowCommandContext.Purchase("USR_01", "PRP_02", 0),
        )
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun successfulEnergyGridPurchasePlaysEnergyGridPurchased() {
        val session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val before = session
        val result = indiaEngine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))
        val context = WorkflowCommandContext.EnergyGridPurchase("USR_01", "ENG_01", before.players["USR_01"]!!.balance)
        assertEquals(
            GameplayAudioCue.ENERGY_GRID_PURCHASED,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
        playWorkflow(result, before, context)
        assertEquals(listOf("ENERGY_GRID_PURCHASED"), audio.gameplayCalls)
    }

    @Test
    fun propertyAndEnergyGridPurchaseSoundsAreNotConfused() {
        val ukSession = AppTestSupport.newGame()
        val propertyResult = ukEngine.process(ukSession, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val propertyCue = GameplayOutcomeAudio.resolvePrimaryAssetCue(propertyResult, ukSession)
        assertEquals(GameplayAudioCue.PROPERTY_PURCHASED, propertyCue)
        assertFalse(propertyCue == GameplayAudioCue.ENERGY_GRID_PURCHASED)

        val indiaSession = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val gridResult = indiaEngine.process(indiaSession, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))
        val gridCue = GameplayOutcomeAudio.resolvePrimaryAssetCue(gridResult, indiaSession)
        assertEquals(GameplayAudioCue.ENERGY_GRID_PURCHASED, gridCue)
        assertFalse(gridCue == GameplayAudioCue.PROPERTY_PURCHASED)
    }

    @Test
    fun successfulRentPaymentPlaysRentTransfer() {
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
        playWorkflow(result, before, context)
        assertEquals(listOf("RENT_TRANSFER"), audio.gameplayCalls)
    }

    @Test
    fun rentReliefPlaysRentReliefNotRentTransfer() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        session = session.copy(
            properties = session.properties + (
                "PRP_15" to session.properties["PRP_15"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        val before = session
        val result = indiaEngine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))
        val context = WorkflowCommandContext.PropertyLanding("USR_02", "PRP_15")
        assertTrue(result.transactions.any { it.transactionType == TransactionType.RENT_WAIVED })
        assertEquals(
            GameplayAudioCue.RENT_RELIEF,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
        playWorkflow(result, before, context)
        assertEquals(listOf("RENT_RELIEF"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("RENT_TRANSFER"))
    }

    @Test
    fun rentLevelIncreasePlaysOnce() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val before = session
        val result = ukEngine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.PropertyLanding("USR_01", "PRP_01")
        assertEquals(
            GameplayAudioCue.RENT_LEVEL_INCREASED,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
        playWorkflow(result, before, context)
        assertEquals(listOf("RENT_LEVEL_INCREASED"), audio.gameplayCalls)
    }

    @Test
    fun multiPropertyRentIncreasePlaysOnlyOnce() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties + mapOf(
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01", currentRentLevel = 2),
                "PRP_02" to session.properties["PRP_02"]!!.copy(ownerPlayerId = "USR_02", currentRentLevel = 2),
            ),
        )
        val before = session
        val result = indiaEngine.process(
            session,
            GameCommand.ApplyEvent(
                "EVT_16",
                "USR_01",
                propertyId = "PRP_01",
                secondPropertyId = "PRP_02",
                secondPlayerId = "USR_02",
            ),
        )
        assertTrue(GameplayOutcomeAudio.rentLevelsIncreased(before, result))
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_16")),
        )
        assertEquals(GameplayAudioCue.RENT_LEVEL_INCREASED, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_16")),
        )
        assertEquals(listOf("RENT_LEVEL_INCREASED"), audio.gameplayCalls)
    }

    @Test
    fun rentLevelDecreasePlaysOnce() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_01" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 3)
                    "PRP_05" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        val before = session
        val result = ukEngine.process(
            session,
            GameCommand.ApplyEvent("EVT_15", "USR_01", propertyId = "PRP_03"),
        )
        assertTrue(GameplayOutcomeAudio.rentLevelsDecreased(before, result))
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertEquals(GameplayAudioCue.RENT_LEVEL_DECREASED, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_15")),
        )
        assertEquals(listOf("RENT_LEVEL_DECREASED"), audio.gameplayCalls)
    }

    @Test
    fun colorSetCompletionPlaysColorSetComplete() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_03")).session
        val before = session
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_02"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_02", before.players["USR_01"]!!.balance)
        assertEquals(
            GameplayAudioCue.COLOR_SET_COMPLETE,
            GameplayOutcomeAudio.resolveCue(result, before, CommitAudioTrigger.GameWorkflow(context)),
        )
        playWorkflow(result, before, context)
        assertEquals(listOf("COLOR_SET_COMPLETE"), audio.gameplayCalls)
    }

    @Test
    fun colorSetCompletingPurchaseDoesNotAlsoPlayPropertyPurchased() {
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_03")).session
        val before = session
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_02"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_02", before.players["USR_01"]!!.balance)
        playWorkflow(result, before, context)
        assertEquals(listOf("COLOR_SET_COMPLETE"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("PROPERTY_PURCHASED"))
    }

    @Test
    fun eminentDomainSellbackPlaysPropertySold() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
            ),
        )
        val before = session
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_19", "USR_01", propertyId = "PRP_01"))
        assertTrue(GameplayOutcomeAudio.propertySoldToBank(before, result))
        val cue = GameplayOutcomeAudio.resolveCue(
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_19")),
        )
        assertEquals(GameplayAudioCue.PROPERTY_SOLD, cue)
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            result,
            before,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.ApplyEvent("EVT_19")),
        )
        assertEquals(listOf("PROPERTY_SOLD"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("EVENT_APPLIED"))
    }

    @Test
    fun debtOwnershipTransferDoesNotPlayPropertySold() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            players = session.players + (
                "USR_02" to session.players["USR_02"]!!.copy(balance = 0)
            ),
            properties = session.properties + (
                "PRP_10" to session.properties["PRP_10"]!!.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
            ),
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 160,
                reason = DebtReason.RENT,
                propertyId = "PRP_12",
            ),
        )
        val before = session
        val result = ukEngine.process(session, GameCommand.ResolveDebt("PRP_10"))
        assertTrue(result.transactions.any { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE })
        assertFalse(GameplayOutcomeAudio.propertySoldToBank(before, result))
        GameplayOutcomeAudio.playCommittedOutcome(audio, result, before, CommitAudioTrigger.DebtSettled)
        assertFalse(audio.gameplayCalls.contains("PROPERTY_SOLD"))
    }

    @Test
    fun resumeAndDuplicateTriggersDoNotReplayBatch2Sounds() {
        val session = AppTestSupport.newGame()
        audio.reset()
        GameplayOutcomeAudio.playCommittedOutcome(
            audio,
            com.boardbanker.core.engine.GameResult(session = session),
            session,
            CommitAudioTrigger.GameWorkflow(WorkflowCommandContext.Purchase("USR_01", "PRP_01", 1500)),
        )
        assertTrue(audio.gameplayCalls.isEmpty())

        val before = session
        val result = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        val context = WorkflowCommandContext.Purchase("USR_01", "PRP_01", before.players["USR_01"]!!.balance)
        assertNull(GameplayOutcomeAudio.resolveCue(result.copy(error = com.boardbanker.core.error.GameError.Validation("fail")), before, CommitAudioTrigger.GameWorkflow(context)))

        val token = ScanPromptAudio.beginPromptSession()
        ScanPromptAudio.playOnce(audio, token)
        ScanPromptAudio.playOnce(audio, token)
        assertEquals(1, audio.gameplayCalls.count { it == "SCAN_CARD" })
    }

    @Test
    fun batch2SoundRegistryMapsAllIdentifiers() {
        assertEquals("property_purchased", GameSoundRegistry.resourceNameFor(GameSound.PROPERTY_PURCHASED))
        assertEquals("energy_grid_purchased", GameSoundRegistry.resourceNameFor(GameSound.ENERGY_GRID_PURCHASED))
        assertEquals("rent_transfer", GameSoundRegistry.resourceNameFor(GameSound.RENT_TRANSFER))
        assertEquals("rent_relief", GameSoundRegistry.resourceNameFor(GameSound.RENT_RELIEF))
        assertEquals("rent_level_increased", GameSoundRegistry.resourceNameFor(GameSound.RENT_LEVEL_INCREASED))
        assertEquals("rent_level_decreased", GameSoundRegistry.resourceNameFor(GameSound.RENT_LEVEL_DECREASED))
        assertEquals("color_set_complete", GameSoundRegistry.resourceNameFor(GameSound.COLOR_SET_COMPLETE))
        assertEquals("property_sold", GameSoundRegistry.resourceNameFor(GameSound.PROPERTY_SOLD))
    }
}
