package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtBankruptcyUndoTests {
    private val engine = TestFixtures.engine
    private val definitions = TestFixtures.definitions

    @Test
    fun tsDebt001_transferPropertyToCreditor() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_02" to 100))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_12" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
                    "PRP_11" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 4)
                    else -> state
                }
            },
        )
        session = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_12"),
        ).session
        session = engine.process(session, GameCommand.ResolveDebt("PRP_11")).session
        assertEquals("USR_01", session.properties["PRP_11"]!!.ownerPlayerId)
        assertEquals(4, session.properties["PRP_11"]!!.currentRentLevel)
    }

    @Test
    fun tsDebt002_returnPropertyToBank() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 0))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_11", "PRP_10", "PRP_09" ->
                        state.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
                    else -> state
                }
            },
        )
        session = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_07", "USR_01"),
        ).session
        val result = engine.process(session, GameCommand.ResolveDebt("PRP_11"))
        assertNull(result.session.properties["PRP_11"]!!.ownerPlayerId)
        assertEquals(50, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun tsBankruptcy001_insufficientAssets() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 50))
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
            ),
            debtResolution = com.boardbanker.core.model.DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 500,
            ),
        )
        val result = engine.process(session, GameCommand.CheckBankruptcy)
        assertEquals(GameStatus.FINISHED, result.session.status)
        assertTrue(result.session.players["USR_01"]!!.bankrupt)
        assertEquals(GameOutcome.BANKRUPTCY, result.outcome)
    }

    @Test
    fun tsEndgame001_winnerByWealth() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 100, "USR_02" to 800))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_21" -> state.copy(ownerPlayerId = "USR_02")
                    "PRP_01" -> state.copy(ownerPlayerId = "USR_01")
                    else -> state
                }
            },
            status = GameStatus.FINISHED,
            players = session.players.mapValues { (id, p) ->
                if (id == "USR_01") p.copy(bankrupt = true, active = false) else p
            },
        )
        val winner = com.boardbanker.core.rules.WinnerCalculator(definitions).determineWinner(session)
        assertEquals("USR_02", winner)
    }

    @Test
    fun tsUndo001_undoLastRentPayment() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 3)
        session = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        ).session
        val beforeBalance1 = 1500
        val beforeBalance2 = session.players["USR_02"]!!.balance
        val beforeLevel = session.properties["PRP_01"]!!.currentRentLevel
        val result = engine.process(session, GameCommand.UndoLastAction)
        assertEquals(beforeBalance1, result.session.players["USR_01"]!!.balance)
        assertEquals(1500, result.session.players["USR_02"]!!.balance)
        assertEquals(3, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertTrue(result.transactions.any { it.transactionType == TransactionType.UNDO })
    }

    @Test
    fun tsUndo002_eventNotUndoable() {
        var session = TestFixtures.newGame()
        session = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_04", "USR_01", propertyId = "PRP_01"),
        ).session
        val result = engine.process(session, GameCommand.UndoLastAction)
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun tsUndo003_debtBlocksUndo() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_02" to 100))
        session = session.copy(
            properties = session.properties + (
                "PRP_11" to session.properties["PRP_11"]!!.copy(
                    ownerPlayerId = "USR_01",
                    currentRentLevel = 4,
                )
            ),
        )
        session = engine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_11"),
        ).session
        val result = engine.process(session, GameCommand.UndoLastAction)
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }
}
