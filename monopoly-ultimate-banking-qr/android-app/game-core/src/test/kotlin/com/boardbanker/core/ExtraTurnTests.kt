package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SessionRestoreValidator
import com.boardbanker.core.rules.TurnScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExtraTurnTests {
    private lateinit var indiaEngine: DefaultGameEngine
    private lateinit var ukEngine: DefaultGameEngine
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        indiaEngine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))
        ukEngine = TestFixtures.engine as DefaultGameEngine
    }

    @Test
    fun evt24_grantsOnePendingExtraTurn() {
        val session = TestFixtures.indiaGame()
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01"))
        assertTrue(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_GRANTED })
    }

    @Test
    fun normalTurnEndsAndExtraTurnStarts() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_01", result.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.EXTRA, result.session.turnState!!.turnKind)
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals("USR_01", result.extraTurnStartedPlayerId)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_STARTED })
        assertFalse(result.transactions.any { it.transactionType == TransactionType.TURN_ADVANCED })
    }

    @Test
    fun extraTurnIsFullyPlayable() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val purchase = indiaEngine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"))
        assertTrue(purchase.isSuccess)
    }

    @Test
    fun endingExtraTurnRotatesNormally() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_02", result.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.NORMAL, result.session.turnState!!.turnKind)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.TURN_ADVANCED })
    }

    @Test
    fun followingPlayerGetsNormalTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals(TurnKind.NORMAL, session.turnState!!.turnKind)
        assertEquals("USR_02", session.turnState!!.activePlayerId)
    }

    @Test
    fun extraTurnsDoNotStackWhilePending() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val second = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01"))
        assertTrue(second.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(0, second.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_GRANTED })
    }

    @Test
    fun evt24DuringActiveExtraTurnDoesNotChain() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals(TurnKind.EXTRA, session.turnState!!.turnKind)
        val second = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01"))
        assertFalse(second.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(0, second.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_GRANTED })
    }

    @Test
    fun skipConsumesAndCancelsExtraTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_02", result.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.NORMAL, result.session.turnState!!.turnKind)
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(0, result.session.players["USR_01"]!!.pendingSkipTurnCount)
        assertEquals("USR_01", result.extraTurnCancelledBySkipPlayerId)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP })
        assertFalse(result.transactions.any { it.transactionType == TransactionType.TURN_SKIPPED && it.playerId == "USR_01" })
    }

    @Test
    fun skipIsNotAppliedToPlayersLaterScheduledTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val second = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_01", second.session.turnState!!.activePlayerId)
        assertTrue(second.skippedTurnPlayerIds.isEmpty())
    }

    @Test
    fun upcomingOtherPlayerSkipsStillEnforced() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_02")
        val result = TestFixtures.endTurn(session, engine = indiaEngine)
        assertEquals("USR_03", result.session.turnState!!.activePlayerId)
        assertEquals(listOf("USR_02"), result.skippedTurnPlayerIds)
    }

    @Test
    fun jailCancelsPendingExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val result = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01"))
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL })
    }

    @Test
    fun trafficCourtCancelsExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_01"))
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(1, result.transactions.count { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL })
    }

    @Test
    fun jailCancellationAffectsOnlyCorrectPlayer() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingExtra(session, "USR_02")
        val result = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01"))
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertTrue(result.session.players["USR_02"]!!.pendingExtraTurn)
    }

    @Test
    fun activeExtraTurnJailNormalizesTurnKindWithoutPendingExtra() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val result = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01"))
        assertEquals(TurnKind.NORMAL, result.session.turnState!!.turnKind)
        assertFalse(result.session.players["USR_01"]!!.pendingExtraTurn)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL })
    }

    @Test
    fun saveRestoreWithPendingExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val restored = serializer.deserialize(serializer.serialize(session))
        assertTrue(restored.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(TurnKind.NORMAL, restored.turnState!!.turnKind)
    }

    @Test
    fun saveRestoreDuringActiveExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(TurnKind.EXTRA, restored.turnState!!.turnKind)
        assertEquals("USR_01", restored.turnState!!.activePlayerId)
    }

    @Test
    fun saveRestoreAfterSkipCancellation() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertFalse(restored.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(0, restored.players["USR_01"]!!.pendingSkipTurnCount)
        assertEquals("USR_02", restored.turnState!!.activePlayerId)
    }

    @Test
    fun saveRestoreAfterJailCancellation() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertFalse(restored.players["USR_01"]!!.pendingExtraTurn)
        assertTrue(restored.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun saveRestoreAfterExtraTurnCompleted() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals("USR_02", restored.turnState!!.activePlayerId)
        assertEquals(TurnKind.NORMAL, restored.turnState!!.turnKind)
    }

    @Test
    fun undoRestoresPendingExtraTurn() {
        var session = TestFixtures.indiaGame()
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val afterStart = TestFixtures.endTurn(session, engine = indiaEngine).session
        val undone = indiaEngine.process(afterStart, GameCommand.UndoLastAction).session
        assertTrue(undone.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(TurnKind.NORMAL, undone.turnState!!.turnKind)
    }

    @Test
    fun undoRestoresConsumedSkipAndCancelsExtra() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.withPendingSkip(session, "USR_01")
        val afterEnd = TestFixtures.endTurn(session, engine = indiaEngine).session
        val undone = indiaEngine.process(afterEnd, GameCommand.UndoLastAction).session
        assertTrue(undone.players["USR_01"]!!.pendingExtraTurn)
        assertEquals(1, undone.players["USR_01"]!!.pendingSkipTurnCount)
        assertEquals("USR_01", undone.turnState!!.activePlayerId)
    }

    @Test
    fun undoRestoresActivePlayerAndTurnKindAfterExtraTurnEnds() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val afterExtraEnd = TestFixtures.endTurn(session, engine = indiaEngine).session
        val undone = indiaEngine.process(afterExtraEnd, GameCommand.UndoLastAction).session
        assertEquals("USR_01", undone.turnState!!.activePlayerId)
        assertEquals(TurnKind.EXTRA, undone.turnState!!.turnKind)
    }

    @Test
    fun doubleEndTurnRejectedDuringExtraTurnStart() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        val second = indiaEngine.process(session, GameCommand.EndTurn("USR_02"))
        assertEquals(GameOutcome.REJECTED, second.outcome)
        assertEquals("USR_01", second.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.EXTRA, second.session.turnState!!.turnKind)
    }

    @Test
    fun twoPlayerRotationWithExtraTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals("USR_01", session.turnState!!.activePlayerId)
    }

    @Test
    fun fourPlayerRotationWithExtraTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03", "USR_04"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
    }

    @Test
    fun eliminatedPlayerHandlingWithExtraTurn() {
        var session = TestFixtures.indiaGame(listOf("USR_01", "USR_02", "USR_03"))
        session = TestFixtures.withPendingExtra(session, "USR_01")
        val bankrupt = session.players["USR_02"]!!.copy(bankrupt = true, active = false)
        session = session.copy(players = session.players + ("USR_02" to bankrupt))
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        session = TestFixtures.endTurn(session, engine = indiaEngine).session
        assertEquals("USR_03", session.turnState!!.activePlayerId)
    }

    @Test
    fun ukEndTurnRegressionWithoutExtraTurn() {
        val session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        val result = TestFixtures.endTurn(session, engine = ukEngine)
        assertEquals("USR_02", result.session.turnState!!.activePlayerId)
        assertEquals(TurnKind.NORMAL, result.session.turnState!!.turnKind)
        assertNull(result.extraTurnStartedPlayerId)
    }

    @Test
    fun startGameDefaultsTurnKindToNormal() {
        val session = TestFixtures.indiaGame()
        assertEquals(TurnKind.NORMAL, session.turnState!!.turnKind)
    }

    @Test
    fun activeGameWithoutTurnStateFailsRestoreValidation() {
        val session = TestFixtures.indiaGame().copy(turnState = null)
        val problems = SessionRestoreValidator(TestFixtures.loadEdition(EditionIds.INDIA)).validate(session)
        assertTrue(problems.any { it.contains("missing turn state") })
    }

    @Test
    fun schedulerSafetyBoundIsExplicit() {
        assertEquals(16, TurnScheduler.MAX_SCHEDULING_ITERATIONS)
    }
}
