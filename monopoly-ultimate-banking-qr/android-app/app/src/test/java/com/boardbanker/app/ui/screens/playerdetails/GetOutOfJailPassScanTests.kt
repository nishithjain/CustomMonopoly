package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.scanner.ScanContext
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.jailPassEventIds
import com.boardbanker.core.model.supportsJailPassScan
import com.boardbanker.app.util.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetOutOfJailPassScanTests {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun createIndiaJailedViewModel(activePlayerId: String = "USR_02"): PlayerDetailsViewModel {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                manager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        if (activePlayerId == "USR_02") {
            session = (
                manager.processCommand(session, GameCommand.EndTurn("USR_01")) as ProcessCommitResult.Committed
                ).session
        }
        session = (
            manager.processCommand(session, GameCommand.SendPlayerToJail(activePlayerId))
                as ProcessCommitResult.Committed
            ).session

        return PlayerDetailsViewModel(
            playerId = activePlayerId,
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
    }

    @Test
    fun getOutOfJailChoice_showsPayScanAndCancelForIndia() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        viewModel.onGetOutOfJail()
        advanceUntilIdle()

        assertEquals(PlayerDetailsStep.GetOutOfJailChoice, viewModel.uiState.value.step)
        assertTrue(viewModel.supportsJailPassScan())
        assertEquals(
            formatMoney(
                AppTestSupport.editionRepository.load(EditionIds.INDIA).bankingValues.jailReleaseFee,
                AppTestSupport.editionRepository.load(EditionIds.INDIA),
            ),
            viewModel.jailFeeText(),
        )
    }

    @Test
    fun ukEdition_doesNotOfferJailPassScan() = runTest {
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        var session = (manager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        session = (
            manager.processCommand(session, GameCommand.SendPlayerToJail("USR_01"))
                as ProcessCommitResult.Committed
            ).session

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = AppTestSupport.definitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.supportsJailPassScan())
        assertFalse(AppTestSupport.definitions.supportsJailPassScan())
    }

    @Test
    fun indiaEdition_buildsRestrictedScannerRequestFromActionType() {
        val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val request = ScanRequest.getOutOfJailPass(definitions.jailPassEventIds())
        assertEquals(ScanContext.GET_OUT_OF_JAIL_PASS, request.context)
        assertEquals(setOf("EVT_11"), request.allowedEventIds)
        assertEquals("Scan Get out of Jail Pass", request.instruction)
    }

    @Test
    fun validJailPassScan_releasesPlayerWithoutFee() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        val balanceBefore = viewModel.uiState.value.balanceText

        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("EVT_11", CardType.EVENT)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(balanceBefore, viewModel.uiState.value.balanceText)
        assertTrue(viewModel.uiState.value.result!!.title.contains("out of Jail"))
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("No Jail fee was charged"))
    }

    @Test
    fun invalidEventScan_doesNotChangeState() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        val balanceBefore = viewModel.uiState.value.balanceText

        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("EVT_01", CardType.EVENT)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.inJail)
        assertEquals(balanceBefore, viewModel.uiState.value.balanceText)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun invalidPropertyScan_doesNotChangeState() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("PRP_01", CardType.PROPERTY)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.inJail)
    }

    @Test
    fun payJailFee_stillWorksFromChoiceScreen() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val expectedBalance = formatMoney(
            indiaDefinitions.bankingValues.startingBalance - indiaDefinitions.bankingValues.jailReleaseFee,
            indiaDefinitions,
        )
        assertEquals(expectedBalance, viewModel.uiState.value.balanceText)
    }

    @Test
    fun scannedJailPass_createsDistinctRecentBankingEntry() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val sessionManager = AppTestSupport.sessionManager(repository)
        var game = (sessionManager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            game = (
                sessionManager.processCommand(
                    game,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        game = (sessionManager.processCommand(game, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        game = (
            sessionManager.processCommand(game, GameCommand.SendPlayerToJail("USR_02"))
                as ProcessCommitResult.Committed
            ).session
        sessionManager.processCommand(
            game,
            GameCommand.GetOutOfJailWithPass("USR_02", "EVT_11"),
        )

        val entries = TransactionHistoryEntries.build(
            sessionManager.currentSession()!!,
            indiaDefinitions,
        )
        val entry = entries.first()
        assertTrue(entry.title.contains("got out of Jail"))
        assertEquals("Get out of Jail Pass used • No fee charged", entry.subtitle)
    }

    @Test
    fun duplicateValidScan_onlyReleasesOnce() = runTest {
        val viewModel = createIndiaJailedViewModel()
        advanceUntilIdle()
        viewModel.onScanJailPass()
        viewModel.onJailPassScanned("EVT_11", CardType.EVENT)
        viewModel.onJailPassScanned("EVT_11", CardType.EVENT)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
    }
}
