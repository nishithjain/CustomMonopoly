package com.boardbanker.app.banking

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.AuctionState
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdvancedBankingWorkflowTest {
    private val definitions = AppTestSupport.definitions
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var executor: BankingCommandExecutor

    @Before
    fun setUp() {
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
        executor = BankingCommandExecutor(sessionManager)
    }

    private suspend fun startActiveGame() {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    @Test
    fun goWorkflow_persistsBalance() = runTest {
        startActiveGame()
        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        val outcome = executor.execute(GameCommand.PayGoSalary("USR_01")) as BankingCommitOutcome.Success
        assertEquals(before + definitions.bankingValues.goSalary, outcome.session.players["USR_01"]!!.balance)
    }

    @Test
    fun goUndo_restoresBalance() = runTest {
        startActiveGame()
        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        executor.execute(GameCommand.PayGoSalary("USR_01"))
        val undone = executor.execute(GameCommand.UndoLastAction) as BankingCommitOutcome.Success
        assertEquals(before, undone.session.players["USR_01"]!!.balance)
    }

    @Test
    fun locationWorkflow_deductsFee() = runTest {
        startActiveGame()
        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        val outcome = executor.execute(
            GameCommand.PayLocationFee("USR_01", "PRP_01"),
        ) as BankingCommitOutcome.Success
        assertEquals(before - definitions.bankingValues.locationFee, outcome.session.players["USR_01"]!!.balance)
    }

    @Test
    fun locationFeeOnly_deductsWithoutPropertyLanding() = runTest {
        startActiveGame()
        val before = sessionManager.currentSession()!!.players["USR_01"]!!.balance
        val outcome = executor.execute(
            GameCommand.PayLocationFee(
                "USR_01",
                com.boardbanker.app.gameplay.location.LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID,
            ),
        ) as BankingCommitOutcome.Success
        assertEquals(before - definitions.bankingValues.locationFee, outcome.session.players["USR_01"]!!.balance)
        assertEquals(null, outcome.session.properties["PRP_01"]!!.ownerPlayerId)
    }

    @Test
    fun jailFee_releasesPlayer() = runTest {
        startActiveGame()
        var session = sessionManager.currentSession()!!
        session = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(jailStatus = true)
            ),
        )
        sessionManager.processCommand(session, GameCommand.PayJailFee("USR_01"))
        assertFalse(sessionManager.currentSession()!!.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun auctionBid_incrementsByTwenty() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction("PRP_03", "USR_01"))
        executor.execute(GameCommand.PlaceAuctionBid("USR_01", 20))
        assertEquals(20, sessionManager.currentSession()!!.auction!!.currentBid)
        executor.execute(GameCommand.PlaceAuctionBid("USR_02", 40))
        assertEquals(40, sessionManager.currentSession()!!.auction!!.currentBid)
    }

    @Test
    fun jailedBidder_rejectedByEngine() = runTest {
        startActiveGame()
        var session = sessionManager.currentSession()!!
        session = session.copy(
            players = session.players + (
                "USR_02" to session.players["USR_02"]!!.copy(jailStatus = true)
            ),
            auction = AuctionState("PRP_03", startedByPlayerId = "USR_01"),
        )
        val result = AppTestSupport.engine.process(session, GameCommand.PlaceAuctionBid("USR_02", 20))
        assertEquals(GameOutcome.REJECTED, result.outcome)
    }

    @Test
    fun auctionComplete_assignsOwner() = runTest {
        startActiveGame()
        executor.execute(GameCommand.StartAuction("PRP_03", "USR_01"))
        executor.execute(GameCommand.PlaceAuctionBid("USR_01", 20))
        val outcome = executor.execute(GameCommand.CompleteAuction) as BankingCommitOutcome.Success
        assertEquals("USR_01", outcome.session.properties["PRP_03"]!!.ownerPlayerId)
    }

    @Test
    fun undoEligibility_blocksDuringDebt() {
        val session = AppTestSupport.newGame().copy(
            debtResolution = DebtResolutionState(
                debtorPlayerId = "USR_01",
                creditorPlayerId = EntityRef.BANK,
                amountRemaining = 100,
            ),
        )
        assertFalse(UndoEligibility(definitions).canUndo(session))
    }

    @Test
    fun sendPlayerToJail_setsJailStatus() = runTest {
        startActiveGame()
        val outcome = executor.execute(GameCommand.SendPlayerToJail("USR_01")) as BankingCommitOutcome.Success
        assertTrue(outcome.session.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun finishedGame_statusIsFinished() {
        val session = AppTestSupport.newGame().copy(status = GameStatus.FINISHED)
        assertEquals(GameStatus.FINISHED, session.status)
    }
}
