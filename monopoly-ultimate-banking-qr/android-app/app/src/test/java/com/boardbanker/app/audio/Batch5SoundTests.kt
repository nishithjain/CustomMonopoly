package com.boardbanker.app.audio

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 5 auction and game-ending sound verification.
 */
class Batch5SoundTests {
    private val engine = AppTestSupport.engine
    private lateinit var audio: RecordingGameAudioFeedback

    @Before
    fun setUp() {
        audio = RecordingGameAudioFeedback()
        ScanPromptAudio.resetForTests()
    }

    private fun playAuction(
        result: com.boardbanker.core.engine.GameResult,
        before: com.boardbanker.core.model.GameSession,
        trigger: CommitAudioTrigger,
    ) {
        GameplayOutcomeAudio.playCommittedOutcome(audio, result, before, trigger)
    }

    @Test
    fun successfulAuctionStartPlaysAuctionBeginsOnce() {
        val session = AppTestSupport.newGame()
        val before = session
        val result = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01"))
        assertEquals(
            listOf(GameplayAudioCue.AUCTION_BEGINS),
            GameplayOutcomeAudio.resolveAuctionStartedCues(result, before),
        )
        playAuction(result, before, CommitAudioTrigger.AuctionStarted)
        assertEquals(listOf("AUCTION_BEGINS"), audio.gameplayCalls)
    }

    @Test
    fun failedAuctionStartPlaysNothing() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01")).session
        val before = session
        val result = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_04", startedByPlayerId = "USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertTrue(GameplayOutcomeAudio.resolveAuctionStartedCues(result, before).isEmpty())
        playAuction(result, before, CommitAudioTrigger.AuctionStarted)
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun restoredAuctionDoesNotReplayAuctionBegins() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01")).session
        val before = session
        val restored = com.boardbanker.core.engine.GameResult(session = session)
        assertTrue(GameplayOutcomeAudio.resolveAuctionStartedCues(restored, before).isEmpty())
    }

    @Test
    fun auctionFinalizationPlaysAuctionEndingOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01")).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_01", 20)).session
        val before = session
        val result = engine.process(session, GameCommand.CompleteAuction)
        assertEquals(
            listOf(GameplayAudioCue.AUCTION_ENDING),
            GameplayOutcomeAudio.resolveAuctionEndingCues(result, before),
        )
        playAuction(result, before, CommitAudioTrigger.AuctionEnding)
        assertEquals(listOf("AUCTION_ENDING"), audio.gameplayCalls)
    }

    @Test
    fun noBidAuctionPlaysAuctionEndingOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01")).session
        val before = session
        val result = engine.process(session, GameCommand.CancelAuction)
        assertEquals(
            listOf(GameplayAudioCue.AUCTION_ENDING),
            GameplayOutcomeAudio.resolveAuctionEndingCues(result, before),
        )
        playAuction(result, before, CommitAudioTrigger.AuctionEnding)
        assertEquals(listOf("AUCTION_ENDING"), audio.gameplayCalls)
    }

    @Test
    fun auctionPurchaseDoesNotPlayPropertyPurchased() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_03", startedByPlayerId = "USR_01")).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_01", 20)).session
        val before = session
        val result = engine.process(session, GameCommand.CompleteAuction)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE })
        assertNull(
            GameplayOutcomeAudio.resolvePrimaryAssetCue(result, before),
        )
        playAuction(result, before, CommitAudioTrigger.AuctionEnding)
        assertFalse(audio.gameplayCalls.contains("PROPERTY_PURCHASED"))
        assertFalse(audio.gameplayCalls.contains("ENERGY_GRID_PURCHASED"))
    }

    @Test
    fun colorSetAuctionWinOverridesAuctionEnding() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        session = engine.process(session, GameCommand.StartAuction(propertyId = "PRP_02", startedByPlayerId = "USR_01")).session
        session = engine.process(session, GameCommand.PlaceAuctionBid("USR_01", 20)).session
        val before = session
        val result = engine.process(session, GameCommand.CompleteAuction)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.COLOR_SET_COMPLETION_BONUS })
        assertEquals(
            listOf(GameplayAudioCue.COLOR_SET_COMPLETE),
            GameplayOutcomeAudio.resolveAuctionEndingCues(result, before),
        )
        playAuction(result, before, CommitAudioTrigger.AuctionEnding)
        assertEquals(listOf("COLOR_SET_COMPLETE"), audio.gameplayCalls)
        assertFalse(audio.gameplayCalls.contains("AUCTION_ENDING"))
    }

    @Test
    fun newBankruptcyPlaysLostGameOnce() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(balance = 50)
            ),
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 500,
            ),
        )
        val before = session
        val result = engine.process(session, GameCommand.CheckBankruptcy)
        assertEquals(listOf("USR_01"), GameplayOutcomeAudio.playersNewlyBankrupt(before, result))
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onBankruptcyCommitted(audio)
        assertEquals(listOf("LOST_GAME"), audio.gameplayCalls)
    }

    @Test
    fun existingBankruptcyDoesNotReplayLostGame() {
        var session = AppTestSupport.newGame()
        session = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(bankrupt = true, active = false)
            ),
            status = GameStatus.FINISHED,
        )
        val restored = com.boardbanker.core.engine.GameResult(session = session)
        assertTrue(GameplayOutcomeAudio.playersNewlyBankrupt(session, restored).isEmpty())
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onWinnerScreenPresented(audio)
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun concludedGamePlaysWinnerOnce() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onGameConcludedForWinnerPresentation()
        coordinator.onWinnerScreenPresented(audio)
        assertEquals(listOf("WINNER"), audio.gameplayCalls)
    }

    @Test
    fun cancelledEndGameDoesNotPlayWinner() {
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onWinnerScreenPresented(audio)
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun abandonGamePlaysNeitherWinnerNorLostGame() {
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.resetForNewGame()
        assertTrue(audio.gameplayCalls.isEmpty())
    }

    @Test
    fun bankruptcyAndWinnerSoundsDoNotOverlap() {
        val coordinator = GameEndAudioCoordinator(winnerDelayMs = 0L)
        coordinator.onBankruptcyCommitted(audio)
        assertEquals(listOf("LOST_GAME"), audio.gameplayCalls)
        coordinator.onWinnerScreenPresented(audio)
        assertEquals(listOf("LOST_GAME", "WINNER"), audio.gameplayCalls)
    }

    @Test
    fun batch5SoundRegistryMapsAllIdentifiers() {
        assertEquals("auction_begins", GameSoundRegistry.resourceNameFor(GameSound.AUCTION_BEGINS))
        assertEquals("auction_ending", GameSoundRegistry.resourceNameFor(GameSound.AUCTION_ENDING))
        assertEquals("winner", GameSoundRegistry.resourceNameFor(GameSound.WINNER))
        assertEquals("lost_game", GameSoundRegistry.resourceNameFor(GameSound.LOST_GAME))
    }
}
