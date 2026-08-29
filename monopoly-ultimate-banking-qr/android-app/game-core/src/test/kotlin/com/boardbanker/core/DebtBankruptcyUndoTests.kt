package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.WinnerCalculator
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
    fun tsDebt003_playerToPlayerChangeReturnedFromCreditor() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 1200, "USR_02" to 0))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_10" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
                    else -> state
                }
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 160,
                reason = DebtReason.RENT,
                propertyId = "PRP_12",
            ),
        )

        val wealthBefore = totalWealth(session)
        val result = engine.process(session, GameCommand.ResolveDebt("PRP_10"))

        assertNull(result.session.debtResolution)
        assertEquals("USR_01", result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals(20, result.session.players["USR_02"]!!.balance)
        assertEquals(1180, result.session.players["USR_01"]!!.balance)
        assertTrue(
            result.transactions.any {
                it.transactionType == TransactionType.RENT_PAYMENT &&
                    it.fromEntity == "USR_01" &&
                    it.toEntity == "USR_02" &&
                    it.amount == 20
            },
        )
        assertEquals(wealthBefore, totalWealth(result.session))
    }

    @Test
    fun tsDebt004_multiplePropertiesApplySingleChange() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 1500, "USR_02" to 0))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_10" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
                    "PRP_11" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 1)
                    else -> state
                }
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 300,
            ),
        )

        val result = engine.process(
            session,
            GameCommand.ResolveDebtWithProperties(listOf("PRP_10", "PRP_11")),
        )

        assertNull(result.session.debtResolution)
        assertEquals("USR_01", result.session.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals("USR_01", result.session.properties["PRP_11"]!!.ownerPlayerId)
        assertEquals(80, result.session.players["USR_02"]!!.balance)
        assertEquals(1420, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun tsDebt005_bankDebtReturnsChangeWithoutDebitingPlayer() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 0))
        session = session.copy(
            properties = session.properties + (
                "PRP_11" to session.properties["PRP_11"]!!.copy(ownerPlayerId = "USR_01", currentRentLevel = 4)
            ),
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 160,
            ),
        )

        val result = engine.process(session, GameCommand.ResolveDebt("PRP_11"))
        assertEquals(40, result.session.players["USR_01"]!!.balance)
        assertNull(result.session.properties["PRP_11"]!!.ownerPlayerId)
        assertTrue(
            result.transactions.any {
                it.transactionType == TransactionType.BANK_CREDIT &&
                    it.fromEntity == EntityRef.BANK &&
                    it.amount == 40
            },
        )
    }

    @Test
    fun tsDebt006_settlementUndoRestoresBalancesOwnershipAndDebt() {
        var session = TestFixtures.sessionWithBalances(mapOf("USR_01" to 1200, "USR_02" to 0))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_10" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 3)
                    else -> state
                }
            },
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_02",
                creditorPlayerId = "USR_01",
                amountRemaining = 160,
            ),
        )

        val settled = engine.process(session, GameCommand.ResolveDebt("PRP_10")).session
        assertNotNull(settled.undoSnapshot)
        val undone = engine.process(settled, GameCommand.UndoLastAction).session

        assertEquals(0, undone.players["USR_02"]!!.balance)
        assertEquals(1200, undone.players["USR_01"]!!.balance)
        assertEquals("USR_02", undone.properties["PRP_10"]!!.ownerPlayerId)
        assertEquals(160, undone.debtResolution!!.amountRemaining)
    }

    private fun totalWealth(session: com.boardbanker.core.model.GameSession): Int {
        val calculator = WinnerCalculator(definitions)
        return session.players.keys.sumOf { playerId ->
            if (session.players[playerId]!!.bankrupt) 0 else calculator.calculateWealth(session, playerId)
        }
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
