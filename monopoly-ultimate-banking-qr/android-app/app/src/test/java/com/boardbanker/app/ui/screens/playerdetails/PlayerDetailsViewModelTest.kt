package com.boardbanker.app.ui.screens.playerdetails

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.banking.BankingCommandExecutor
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var executor: BankingCommandExecutor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
        executor = BankingCommandExecutor(sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun startActiveGame() {
        var session = (sessionManager.createNewGame(EditionIds.UK) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (sessionManager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        sessionManager.processCommand(session, GameCommand.StartGame)
    }

    private fun createViewModel(playerId: String = "USR_01"): PlayerDetailsViewModel =
        PlayerDetailsViewModel(
            playerId = playerId,
            sessionManager = sessionManager,
            definitions = AppTestSupport.definitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )

    @Test
    fun playerDetails_showsCustomNameAndBalance() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Nishith", viewModel.uiState.value.playerName)
        assertEquals("M1500", viewModel.uiState.value.balanceText)
        assertEquals("Car", viewModel.uiState.value.tokenName)
    }

    @Test
    fun collectGo_updatesPlayerDetailsBalanceImmediately() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCollectGo()
        viewModel.onConfirmGo()
        advanceUntilIdle()

        assertEquals("M1700", viewModel.uiState.value.balanceText)
    }

    @Test
    fun sendToJail_updatesJailStatusImmediately() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onGoToJail()
        viewModel.onConfirmGoToJail()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.inJail)
        assertEquals("In Jail", viewModel.uiState.value.playerStatusText)
    }

    @Test
    fun payJailFee_clearsJailAndUpdatesBalance() = runTest {
        startActiveGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals("M1400", viewModel.uiState.value.balanceText)
    }

    @Test
    fun useJailPass_releasesPlayerWithoutChargingFee() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val indiaExecutor = BankingCommandExecutor(manager)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        val balanceBefore = session.players["USR_01"]!!.balance
        indiaExecutor.execute(GameCommand.ApplyEvent("EVT_11", "USR_01"))
        indiaExecutor.execute(GameCommand.SendPlayerToJail("USR_01"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.jailPassCount)
        viewModel.onGetOutOfJail()
        assertEquals("Use Jail Pass", viewModel.jailPassActionLabel())
        viewModel.onUseJailPass()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        assertEquals(0, viewModel.uiState.value.jailPassCount)
        assertEquals(balanceBefore, manager.currentSession()!!.players["USR_01"]!!.balance)
        assertTrue(viewModel.uiState.value.result!!.primaryMessage.contains("No Jail fee was charged"))
    }

    @Test
    fun jailPassActionLabel_showsCountWhenMultiplePasses() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)

        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_01", "Nishith")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.RegisterPlayer("USR_02", "Aditya")) as ProcessCommitResult.Committed).session
        session = (manager.processCommand(session, GameCommand.StartGame) as ProcessCommitResult.Committed).session
        val jailedWithTwoPasses = session.copy(
            players = session.players + (
                "USR_01" to session.players["USR_01"]!!.copy(jailStatus = true, jailPassCount = 2)
            ),
        )
        repository.save(jailedWithTwoPasses)
        manager.restoreFromStorage()

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals("Use Jail Pass (2)", viewModel.jailPassActionLabel())
    }

    @Test
    fun jailedPlayer_disablesBankActionsExceptGetOutOfJail() = runTest {
        startActiveGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel("USR_01")
        advanceUntilIdle()

        val availability = PlayerDetailsActionAvailability.forPlayer(
            inJail = viewModel.uiState.value.inJail,
            commandInFlight = viewModel.uiState.value.commandInFlight,
            step = viewModel.uiState.value.step,
        )
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertTrue(availability.getOutOfJailEnabled)
        assertFalse(availability.goToJailEnabled)
    }

    @Test
    fun nonJailedPlayer_keepsNormalBankActionsWhenOtherPlayerJailed() = runTest {
        startActiveGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel("USR_02")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        val availability = PlayerDetailsActionAvailability.forPlayer(
            inJail = viewModel.uiState.value.inJail,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertTrue(availability.collectGoEnabled)
        assertTrue(availability.locationEnabled)
        assertTrue(availability.goToJailEnabled)
    }

    @Test
    fun successfulJailRelease_restoresNormalBankActions() = runTest {
        startActiveGame()
        executor.execute(GameCommand.SendPlayerToJail("USR_01"))
        val viewModel = createViewModel("USR_01")
        advanceUntilIdle()

        viewModel.onGetOutOfJail()
        viewModel.onPayJailFee()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inJail)
        val availability = PlayerDetailsActionAvailability.forPlayer(
            inJail = viewModel.uiState.value.inJail,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertTrue(availability.collectGoEnabled)
        assertTrue(availability.locationEnabled)
        assertTrue(availability.goToJailEnabled)
    }

    @Test
    fun ownedProperties_sortedByBoardSequence() = runTest {
        startActiveGame()
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_22"))
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_01"))
        executor.execute(GameCommand.PurchaseProperty("USR_01", "PRP_05"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("PRP_01", "PRP_05", "PRP_22"), viewModel.uiState.value.ownedProperties.map { it.propertyId })
        assertTrue(viewModel.uiState.value.ownedProperties.all { it.currentRentText.startsWith("M") })
        assertEquals("Brown", viewModel.uiState.value.ownedProperties.first { it.propertyId == "PRP_01" }.colorGroupLabel)
    }

    @Test
    fun ukEditionHidesEnergyGridSection() = runTest {
        startActiveGame()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasEnergyGridsInEdition)
        assertTrue(viewModel.uiState.value.ownedEnergyGrids.isEmpty())
        assertEquals(0, viewModel.uiState.value.energyGridCount)
    }

    @Test
    fun indiaPlayerShowsOwnedEnergyGridWithFormattedMoney() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        startIndiaGame(manager)
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.energyGridCount)
        assertEquals(1, viewModel.uiState.value.totalAssetCount)
        assertEquals("ENG_01", viewModel.uiState.value.ownedEnergyGrids.single().energyGridId)
        assertEquals("Solar Energy", viewModel.uiState.value.ownedEnergyGrids.single().energyGridName)
        assertTrue(viewModel.uiState.value.ownedEnergyGrids.single().currentRentText.contains("5"))
        assertTrue(viewModel.uiState.value.ownedEnergyGrids.single().purchasePriceText.contains("20"))
    }

    @Test
    fun anotherPlayersEnergyGridIsNotShown() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        startIndiaGame(manager)
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_02", "ENG_02"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.ownedEnergyGrids.isEmpty())
        assertEquals(0, viewModel.uiState.value.energyGridCount)
    }

    @Test
    fun fourPropertiesAndOneEnergyGridShowCorrectCounts() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        startIndiaGame(manager)
        listOf("PRP_01", "PRP_02", "PRP_03", "PRP_04").forEach { propertyId ->
            executor.execute(GameCommand.PurchaseProperty("USR_01", propertyId))
        }
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_01", "ENG_03"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.propertyCount)
        assertEquals(1, viewModel.uiState.value.energyGridCount)
        assertEquals(5, viewModel.uiState.value.totalAssetCount)
        assertEquals(4, viewModel.uiState.value.ownedProperties.size)
        assertEquals(1, viewModel.uiState.value.ownedEnergyGrids.size)
    }

    @Test
    fun undoRemovesOwnedEnergyGridFromPlayerDetails() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        startIndiaGame(manager)
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.energyGridCount)

        executor.execute(GameCommand.UndoLastAction)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.energyGridCount)
        assertTrue(viewModel.uiState.value.ownedEnergyGrids.isEmpty())
    }

    @Test
    fun restoredSessionRebuildsOwnedEnergyGrid() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        val serializer = KotlinGameSessionSerializer()
        startIndiaGame(manager)
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_01", "ENG_04"))
        val saved = manager.currentSession()!!

        repository.deleteAll()
        repository.save(serializer.deserialize(serializer.serialize(saved)))
        manager.restoreFromStorage()

        val viewModel = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertEquals("ENG_04", viewModel.uiState.value.ownedEnergyGrids.single().energyGridId)
    }

    @Test
    fun ownershipTransferUpdatesEnergyGridLists() = runTest {
        val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        val repository = FakeGameSessionRepository()
        val manager = AppTestSupport.sessionManager(repository)
        val executor = BankingCommandExecutor(manager)
        startIndiaGame(manager)
        executor.execute(GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))

        val transferred = manager.currentSession()!!.copy(
            energyGrids = manager.currentSession()!!.energyGrids + (
                "ENG_01" to manager.currentSession()!!.energyGrids["ENG_01"]!!.copy(ownerPlayerId = "USR_02")
            ),
        )
        repository.save(transferred)
        manager.restoreFromStorage()

        val formerOwner = PlayerDetailsViewModel(
            playerId = "USR_01",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        val newOwner = PlayerDetailsViewModel(
            playerId = "USR_02",
            sessionManager = manager,
            definitions = indiaDefinitions,
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        advanceUntilIdle()

        assertTrue(formerOwner.uiState.value.ownedEnergyGrids.isEmpty())
        assertEquals("ENG_01", newOwner.uiState.value.ownedEnergyGrids.single().energyGridId)
    }

    private suspend fun startIndiaGame(
        manager: ActiveGameSessionManager,
    ) {
        var session = (manager.createNewGame(EditionIds.INDIA) as ProcessCommitResult.Committed).session
        for (playerId in listOf("USR_01", "USR_02")) {
            session = (
                manager.processCommand(
                    session,
                    GameCommand.RegisterPlayer(playerId, AppTestSupport.defaultTestPlayerName(playerId)),
                ) as ProcessCommitResult.Committed
                ).session
        }
        manager.processCommand(session, GameCommand.StartGame)
    }
}
