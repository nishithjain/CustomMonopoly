package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.ui.screens.game.GameViewModel
import com.boardbanker.app.ui.screens.history.HistoryDetail
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.money.MoneyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RentReliefWorkflowTest {
    private val testDispatcher = StandardTestDispatcher()
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = com.boardbanker.core.engine.DefaultGameEngine(indiaDefinitions)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rentWaivedResultShownWhenLandingWithRelief() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        var session = startIndiaGameWithOwnedProperty(sessionManager)
        session = (
            sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_10", "USR_02"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = createViewModel(sessionManager)
        advanceUntilIdle()

        viewModel.onCardScanned("PRP_15", CardType.PROPERTY)
        advanceUntilIdle()

        val result = viewModel.uiState.value.result
        assertEquals("RENT WAIVED", result?.title)
        assertTrue(result!!.primaryMessage.contains("Rent Relief"))
        assertTrue(result.primaryMessage.contains(MoneyFormatter.format(
            indiaDefinitions.properties["PRP_15"]!!.rentLevels.first { it.level == 1 }.amount,
            indiaDefinitions,
        )))
        assertNull(viewModel.uiState.value.scanRequest)
        assertNull(sessionManager.currentSession()!!.debtResolution)
    }

    @Test
    fun recentBankingRecordsRentWaivedWithoutBalanceChange() = runTest {
        val sessionManager = AppTestSupport.sessionManager(FakeGameSessionRepository())
        var session = startIndiaGameWithOwnedProperty(sessionManager)
        session = (
            sessionManager.processCommand(session, GameCommand.ApplyEvent("EVT_10", "USR_02"))
                as ProcessCommitResult.Committed
            ).session
        val payerBefore = session.players["USR_02"]!!.balance
        val ownerBefore = session.players["USR_01"]!!.balance
        session = (
            sessionManager.processCommand(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))
                as ProcessCommitResult.Committed
            ).session

        assertEquals(payerBefore, session.players["USR_02"]!!.balance)
        assertEquals(ownerBefore, session.players["USR_01"]!!.balance)
        val waivedEntries = TransactionHistoryEntries.build(session, indiaDefinitions)
            .filter { it.title == "Rent waived" }
        assertEquals(1, waivedEntries.size)
        val detail = waivedEntries.single().detail as HistoryDetail.RentWaived
        assertEquals("Aditya", detail.landingPlayerName)
        assertEquals("Nishith", detail.ownerPlayerName)
        assertTrue(detail.propertyName.contains("15"))
        assertTrue(detail.reason.contains("Rent Relief"))
    }

    @Test
    fun mapperShowsRentWaivedMessageWithFormattedAmount() {
        var session = indiaSessionWithOwnedProperty()
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_02")).session
        val before = session
        val result = indiaEngine.process(session, GameCommand.ProcessPropertyLanding("USR_02", "PRP_15"))
        val ui = GameplayResultMapper(indiaDefinitions).mapPropertyLandingResult(
            result,
            "USR_02",
            "PRP_15",
            before,
        )
        val expectedRent = indiaDefinitions.properties["PRP_15"]!!
            .rentLevels.first { it.level == 1 }.amount

        assertEquals("RENT WAIVED", ui.title)
        assertTrue(ui.primaryMessage.contains("No rent needs to be paid because"))
        assertTrue(ui.primaryMessage.contains(MoneyFormatter.format(expectedRent, indiaDefinitions)))
        assertTrue(ui.primaryMessage.contains("Owner:"))
        assertEquals(GameOutcome.SUCCESS, result.outcome)
    }

    private fun indiaSessionWithOwnedProperty(
        propertyId: String = "PRP_15",
        ownerId: String = "USR_01",
    ): com.boardbanker.core.model.GameSession {
        var session = (
            com.boardbanker.core.engine.DefaultGameEngine(indiaDefinitions).process(
                com.boardbanker.core.model.GameSession(gameId = "RENT_RELIEF_TEST", editionId = EditionIds.INDIA),
                GameCommand.CreateGame("RENT_RELIEF_TEST"),
            )
        ).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = indiaEngine.process(
                session,
                GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
            ).session
        }
        session = indiaEngine.process(session, GameCommand.StartGame).session
        session = indiaEngine.process(session, GameCommand.PurchaseProperty(ownerId, propertyId)).session
        session = indiaEngine.process(session, GameCommand.EndTurn(ownerId)).session
        return session
    }

    private fun createViewModel(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
    ): GameViewModel = GameViewModel(
        sessionManager = sessionManager,
        definitions = indiaDefinitions,
        transientWorkflow = TransientScanWorkflowHolder(),
        locationWorkflowHolder = LocationWorkflowHolder(),
        gameAudioFeedback = RecordingGameAudioFeedback(),
        gameEndAudioCoordinator = GameEndAudioCoordinator(),
    )

    private suspend fun startIndiaGameWithOwnedProperty(
        sessionManager: com.boardbanker.app.game.ActiveGameSessionManager,
        propertyId: String = "PRP_15",
        ownerId: String = "USR_01",
    ): com.boardbanker.core.model.GameSession {
        var session = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                sessionManager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        session = (sessionManager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        session = (
            sessionManager.processCommand(session, GameCommand.PurchaseProperty(ownerId, propertyId))
                as ProcessCommitResult.Committed
            ).session
        session = (
            sessionManager.processCommand(session, GameCommand.EndTurn(ownerId))
                as ProcessCommitResult.Committed
            ).session
        return session
    }
}
