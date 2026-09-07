package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.isJailPassEvent
import com.boardbanker.core.model.jailPassEventIds
import com.boardbanker.core.model.supportsJailPassScan
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JailPassTests {
    private lateinit var indiaEngine: DefaultGameEngine
    private lateinit var ukEngine: DefaultGameEngine
    private val indiaDefinitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        indiaEngine = DefaultGameEngine(indiaDefinitions)
        ukEngine = DefaultGameEngine(TestFixtures.definitions)
    }

    @Test
    fun evt11_grantsJailPass() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01"))
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun jailedPlayerUsesPass_releasesFromJail() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val result = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.JAIL_PASS_USED })
        assertTrue(result.transactions.any { it.transactionType == TransactionType.JAIL_STATUS_CHANGE })
    }

    @Test
    fun usingPass_doesNotChangeBalance() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val balanceBefore = session.players["USR_01"]!!.balance
        val result = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(balanceBefore, result.session.players["USR_01"]!!.balance)
        assertFalse(result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT })
    }

    @Test
    fun usingPass_decrementsPassCountOnce() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01", passCount = 2)
        val result = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun jailedPlayerMayPayFeeInstead() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val balanceBefore = session.players["USR_01"]!!.balance
        val fee = indiaDefinitions.bankingValues.jailReleaseFee
        val result = indiaEngine.process(session, GameCommand.PayJailFee("USR_01"))
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(balanceBefore - fee, result.session.players["USR_01"]!!.balance)
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun payingFee_doesNotConsumePass() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val result = indiaEngine.process(session, GameCommand.PayJailFee("USR_01"))
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun useWithoutPass_isRejected() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01", passCount = 0)
        val result = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(0, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun useWhileNotJailed_isRejected() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA).let { base ->
            val player = base.players["USR_01"]!!.copy(jailPassCount = 1)
            base.copy(players = base.players + ("USR_01" to player))
        }
        val result = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun doubleSubmission_secondAttemptIsRejected() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val first = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        val second = indiaEngine.process(first.session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(GameOutcome.REJECTED, second.outcome)
        assertEquals(0, second.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun saveRestore_beforeUse_preservesPassAndJail() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(session, restored)
        assertTrue(restored.players["USR_01"]!!.jailStatus)
        assertEquals(1, restored.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun saveRestore_afterUse_preservesConsumedPass() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val afterUse = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(afterUse))
        assertEquals(afterUse, restored)
        assertFalse(restored.players["USR_01"]!!.jailStatus)
        assertEquals(0, restored.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun undo_restoresJailAndPass() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val afterUse = indiaEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01")).session
        val undone = indiaEngine.process(afterUse, GameCommand.UndoLastAction).session
        assertTrue(undone.players["USR_01"]!!.jailStatus)
        assertEquals(1, undone.players["USR_01"]!!.jailPassCount)
        assertEquals(session.players["USR_01"]!!.balance, undone.players["USR_01"]!!.balance)
    }

    @Test
    fun ukPayJailFee_unchanged() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val balanceBefore = session.players["USR_01"]!!.balance
        val fee = TestFixtures.definitions.bankingValues.jailReleaseFee
        val result = ukEngine.process(session, GameCommand.PayJailFee("USR_01"))
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(balanceBefore - fee, result.session.players["USR_01"]!!.balance)
        assertEquals(0, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun ukUsePass_withoutPass_isRejected() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val result = ukEngine.process(session, GameCommand.UseGetOutOfJailPass("USR_01"))
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertNotEquals(GameOutcome.REJECTED, ukEngine.process(session, GameCommand.PayJailFee("USR_01")).outcome)
    }

    @Test
    fun scannedJailPass_releasesFromJailWithoutFee() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val balanceBefore = session.players["USR_01"]!!.balance
        val result = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"),
        )
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(balanceBefore, result.session.players["USR_01"]!!.balance)
        assertEquals(0, result.session.players["USR_01"]!!.jailPassCount)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.JAIL_PASS_USED && it.eventId == "EVT_11" })
        assertTrue(result.transactions.any { it.transactionType == TransactionType.JAIL_STATUS_CHANGE })
        assertFalse(result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT })
    }

    @Test
    fun scannedJailPass_doesNotConsumeStoredInventoryPass() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val result = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"),
        )
        assertFalse(result.session.players["USR_01"]!!.jailStatus)
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test
    fun scannedJailPass_rejectsWrongEventId() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val result = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_01"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun scannedJailPass_rejectsWhenNotJailed() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val result = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun scannedJailPass_doubleSubmission_secondRejected() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val first = indiaEngine.process(session, GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"))
        val second = indiaEngine.process(first.session, GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"))
        assertEquals(GameOutcome.REJECTED, second.outcome)
    }

    @Test
    fun scannedJailPass_undo_restoresJailWithoutConsumingInventory() {
        val session = TestFixtures.sessionWithJailAndPass("USR_01")
        val afterRelease = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"),
        ).session
        val undone = indiaEngine.process(afterRelease, GameCommand.UndoLastAction).session
        assertTrue(undone.players["USR_01"]!!.jailStatus)
        assertEquals(1, undone.players["USR_01"]!!.jailPassCount)
        assertEquals(session.players["USR_01"]!!.balance, undone.players["USR_01"]!!.balance)
    }

    @Test
    fun scannedJailPass_saveRestore_preservesRelease() {
        val session = TestFixtures.sessionWithJail("USR_01")
        val afterRelease = indiaEngine.process(
            session,
            GameCommand.GetOutOfJailWithPass("USR_01", "EVT_11"),
        ).session
        val restored = serializer.deserialize(serializer.serialize(afterRelease))
        assertEquals(afterRelease, restored)
        assertFalse(restored.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun indiaEdition_exposesJailPassEventByActionType() {
        assertTrue(indiaDefinitions.isJailPassEvent("EVT_11"))
        assertEquals(setOf("EVT_11"), indiaDefinitions.jailPassEventIds())
        assertTrue(indiaDefinitions.supportsJailPassScan())
    }

    @Test
    fun ukEdition_doesNotSupportJailPassScan() {
        assertFalse(TestFixtures.definitions.supportsJailPassScan())
        assertTrue(TestFixtures.definitions.jailPassEventIds().isEmpty())
    }
}
