package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.GoCollectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankActionActivePlayerTests {
    private lateinit var engine: DefaultGameEngine

    @Before
    fun setUp() {
        engine = DefaultGameEngine(TestFixtures.definitions)
    }

    @Test
    fun payGoSalaryRejectsNonActivePlayerForManualBankAction() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val result = engine.process(
            session,
            GameCommand.PayGoSalary("USR_01", GoCollectionReason.MANUAL_BANK_ACTION),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun payGoSalaryAppliesToActivePlayerForManualBankAction() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val before = session.players["USR_02"]!!.balance
        val result = engine.process(
            session,
            GameCommand.PayGoSalary("USR_02", GoCollectionReason.MANUAL_BANK_ACTION),
        )
        assertTrue(result.isSuccess)
        assertEquals(before + TestFixtures.definitions.bankingValues.goSalary, result.session.players["USR_02"]!!.balance)
        assertEquals(session.players["USR_01"]!!.balance, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun locationFeeRejectsNonActivePlayerWhenRestricted() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val result = engine.process(
            session,
            GameCommand.PayLocationFee("USR_01", "PRP_01", restrictToActivePlayer = true),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun sendToJailRejectsNonActivePlayerWhenRestricted() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        val result = engine.process(
            session,
            GameCommand.SendPlayerToJail("USR_01", restrictToActivePlayer = true),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun jailedActivePlayerCannotCollectGoViaManualBankAction() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = TestFixtures.sessionWithActivePlayer(session, "USR_02", engine)
        session = session.copy(
            players = session.players + (
                "USR_02" to session.players["USR_02"]!!.copy(jailStatus = true)
            ),
        )
        val result = engine.process(
            session,
            GameCommand.PayGoSalary("USR_02", GoCollectionReason.MANUAL_BANK_ACTION),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }
}
