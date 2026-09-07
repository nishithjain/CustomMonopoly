package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.gameplay.presentation.EventDrawUiMapper
import com.boardbanker.app.gameplay.presentation.EventDrawUiState
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.ui.theme.BankingQRTheme
import com.boardbanker.core.model.EditionIds
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    private fun render(
        viewModel: GameViewModel,
        onNavigateToPlayerDetails: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BankingQRTheme {
                GameScreen(
                    viewModel = viewModel,
                    onNavigateHome = {},
                    onOpenScanner = { _, _ -> },
                    onNavigateToBanking = {},
                    onNavigateToAuction = { _, _, _ -> },
                    onNavigateToDebt = {},
                    onNavigateToGameOver = {},
                    onNavigateToPlayerDetails = onNavigateToPlayerDetails,
                )
            }
        }
    }

    private fun assertTextDoesNotExist(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private fun samplePlayers(activePlayerId: String = "USR_01") = listOf(
        PlayerDashboardUi(
            playerId = "USR_01",
            playerName = "Nishith",
            balanceText = "₹48,000",
            propertyCount = 1,
            energyGridCount = 0,
            hasEnergyGridsInEdition = true,
            isActiveTurn = activePlayerId == "USR_01",
            statusText = "Active",
            assetsSummaryLine = "1 Property • 0 Energy Grids",
        ),
        PlayerDashboardUi(
            playerId = "USR_02",
            playerName = "Aditya",
            balanceText = "₹66,000",
            propertyCount = 4,
            energyGridCount = 1,
            hasEnergyGridsInEdition = true,
            isActiveTurn = activePlayerId == "USR_02",
            statusText = "Active",
            assetsSummaryLine = "4 Properties • 1 Energy Grid",
        ),
    )

    private fun readyHubState(
        activePlayerId: String = "USR_01",
        activePlayerName: String = "Nishith",
        endTurnEnabled: Boolean = true,
        endTurnDisabledReason: String? = null,
        includePlayers: Boolean = true,
    ) = GameUiState(
        loading = false,
        editionId = EditionIds.INDIA,
        workflowState = GameplayWorkflowState.Ready,
        players = if (includePlayers) samplePlayers(activePlayerId) else emptyList(),
        actionAvailability = ActiveGameActionAvailability(
            scanCardEnabled = true,
            endTurnEnabled = endTurnEnabled,
            bankActionsEnabled = true,
            getOutOfJailEnabled = false,
        ),
        activePlayerId = activePlayerId,
        activePlayerName = activePlayerName,
        endTurnSubtitle = ActiveGameEndTurnPresentation.subtitle(activePlayerName),
        endTurnDisabledReason = endTurnDisabledReason,
        endTurnContentDescription = ActiveGameEndTurnPresentation.contentDescription(activePlayerName),
    )

    private fun scrollToHub() {
        if (composeRule.onAllNodesWithText("Players").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Players").performScrollTo()
        }
        composeRule.onNodeWithTag("active_game_hub_actions").performScrollTo()
    }

    @Test
    fun mainHubShowsPrimaryAndTerminationActions() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState(includePlayers = false))

        render(viewModel)
        scrollToHub()

        composeRule.onNodeWithText("Scan Card").assertIsDisplayed()
        composeRule.onNodeWithText("Bank Actions").assertIsDisplayed()
        composeRule.onNodeWithText("END TURN").assertIsDisplayed()
        composeRule.onNodeWithText("End Nishith's turn").assertIsDisplayed()
        composeRule.onNodeWithText("End Game").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Abandon Game").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyPlayerRenderedInsideIndividualCard() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState())

        render(viewModel)

        composeRule.onNodeWithTag("active_game_player_card_USR_01").assertIsDisplayed()
        composeRule.onNodeWithTag("active_game_player_card_USR_02").assertIsDisplayed()
    }

    @Test
    fun activePlayerCardShowsCurrentTurnBadge() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState())

        render(viewModel)

        composeRule.onNodeWithText("Current Turn").assertIsDisplayed()
    }

    @Test
    fun playerCardsShowFormattedBalanceAndAssetCounts() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState())

        render(viewModel)

        composeRule.onNodeWithText("₹48,000").assertIsDisplayed()
        composeRule.onNodeWithText("1 Property • 0 Energy Grids").assertIsDisplayed()
        composeRule.onNodeWithText("₹66,000").assertIsDisplayed()
        composeRule.onNodeWithText("4 Properties • 1 Energy Grid").assertIsDisplayed()
    }

    @Test
    fun onlyEndTurnUsesDoubleHeightLayout() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState(includePlayers = false))

        render(viewModel)
        scrollToHub()

        composeRule.onNodeWithTag("active_game_scan_card_button").assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithTag("active_game_bank_actions_button").assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithTag("active_game_end_turn_button").assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithTag("active_game_end_game_button").assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithTag("active_game_abandon_game_button").assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun endTurnSubtitleChangesWhenActivePlayerChanges() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            readyHubState(
                activePlayerId = "USR_02",
                activePlayerName = "Aditya",
                includePlayers = false,
            ),
        )

        render(viewModel)
        scrollToHub()

        composeRule.onNodeWithText("End Aditya's turn").assertIsDisplayed()
        assertTextDoesNotExist("End Nishith's turn")
    }

    @Test
    fun disabledEndTurnShowsPendingActionReason() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            readyHubState(
                endTurnEnabled = false,
                endTurnDisabledReason = "Complete Lucky Draw before ending Nishith's turn.",
                includePlayers = false,
            ),
        )

        render(viewModel)
        scrollToHub()

        composeRule.onNodeWithTag("active_game_end_turn_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("active_game_end_turn_disabled_reason").assertIsDisplayed()
        composeRule.onNodeWithText("Complete Lucky Draw before ending Nishith's turn.").assertIsDisplayed()
    }

    @Test
    fun tappingPlayerCardOpensPlayerDetails() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(readyHubState())
        var navigatedPlayerId: String? = null

        render(viewModel, onNavigateToPlayerDetails = { navigatedPlayerId = it })

        composeRule.onNodeWithTag("active_game_player_card_USR_02").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { navigatedPlayerId == "USR_02" }
        assertEquals("USR_02", navigatedPlayerId)
    }

    @Test
    fun screenScrollsWithFourPlayers() {
        val fourPlayers = listOf("USR_01", "USR_02", "USR_03", "USR_04").map { playerId ->
            PlayerDashboardUi(
                playerId = playerId,
                playerName = AppTestSupport.defaultTestPlayerName(playerId),
                balanceText = "₹50,000",
                propertyCount = 2,
                energyGridCount = 1,
                hasEnergyGridsInEdition = true,
                isActiveTurn = playerId == "USR_01",
                statusText = "Active",
                assetsSummaryLine = "2 Properties • 1 Energy Grid",
            )
        }
        val hubState = readyHubState(includePlayers = false)

        composeRule.setContent {
            BankingQRTheme {
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .height(240.dp),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item { Text("Players") }
                            items(fourPlayers, key = { it.playerId }) { player ->
                                ActiveGamePlayerCard(player = player, onClick = {})
                            }
                            item {
                                ActiveGameHubActions(
                                    uiState = hubState,
                                    showGameTerminationActions = true,
                                    onScanCard = {},
                                    onGetOutOfJail = {},
                                    onBankActions = {},
                                    onEndTurn = {},
                                    onEndGame = {},
                                    onAbandonGame = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("active_game_player_card_USR_04"))
        composeRule.onNodeWithTag("active_game_player_card_USR_04").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("active_game_end_turn_button"))
        composeRule.onNodeWithTag("active_game_end_turn_button").assertIsDisplayed()
    }

    @Test
    fun propertyPurchaseScreenHidesTerminationActions() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                workflowState = GameplayWorkflowState.UnownedPropertyDecision("PRP_01"),
                cardPresentation = CardPresentationUi(
                    cardTypeLabel = "PROPERTY",
                    title = "Old Kent Road",
                    body = "Purchase Price:\n₹6,000\n\nStatus:\nUNOWNED",
                    buyAmount = 6000,
                ),
            ),
        )

        render(viewModel)

        assertTextDoesNotExist("End Game")
        assertTextDoesNotExist("Abandon Game")
    }

    @Test
    fun energyGridPurchaseScreenShowsBuyAndHidesTerminationActions() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                workflowState = GameplayWorkflowState.UnownedEnergyGridDecision("ENG_01"),
                cardPresentation = CardPresentationUi(
                    cardTypeLabel = "ENERGY GRID",
                    title = "01 Solar Energy",
                    body = "Purchase Price:\n₹20,000",
                    buyAmount = 20000,
                ),
            ),
        )

        render(viewModel)

        composeRule.onNodeWithText("✓ BUY ₹20,000").assertIsDisplayed()
        assertTextDoesNotExist("End Game")
        assertTextDoesNotExist("Abandon Game")
    }

    @Test
    fun jailedEnergyGridPurchaseShowsDisabledBuyWithReason() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                activePlayerInJail = true,
                workflowState = GameplayWorkflowState.UnownedEnergyGridDecision("ENG_01"),
                cardPresentation = CardPresentationUi(
                    cardTypeLabel = "ENERGY GRID",
                    title = "01 Solar Energy",
                    body = "Purchase Price:\n₹20,000",
                    buyAmount = 20000,
                ),
            ),
        )

        render(viewModel)

        composeRule.onNodeWithText("Get out of Jail before purchasing.").assertIsDisplayed()
        composeRule.onNodeWithText("✓ BUY ₹20,000").assertIsNotEnabled()
    }

    @Test
    fun ownedPropertyScanComposeDoesNotShowLandingPlayerPrompt() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = AppTestSupport.definitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.UK,
                workflowState = GameplayWorkflowState.Ready,
                cardPresentation = CardPresentationUi(
                    cardTypeLabel = "PROPERTY",
                    title = "Old Kent Road",
                    body = "Current Rent: £10",
                    buyAmount = null,
                ),
                result = GameplayResultUiModel(
                    title = "RENT",
                    primaryMessage = "Rent paid.",
                ),
            ),
        )

        render(viewModel)

        assertTextDoesNotExist("Scan the Player who landed here.")
        assertTextDoesNotExist("Scan the Player Card of the player who landed on this property")
    }

    @Test
    fun luckyDrawScreenShowsEnabledScanButton() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                workflowState = GameplayWorkflowState.EventDrawScanRequired(
                    parentEventId = "EVT_15",
                    actingPlayerId = "USR_01",
                ),
                eventDraw = EventDrawUiState(
                    parentEventId = "EVT_15",
                    parentEventName = "Lucky Draw",
                    actingPlayerId = "USR_01",
                    actingPlayerName = "Nishith",
                    instruction = EventDrawUiMapper.INSTRUCTION,
                    requiredDrawsText = EventDrawUiMapper.REQUIRED_DRAWS_TEXT,
                    scanButtonLabel = EventDrawUiMapper.SCAN_BUTTON_LABEL,
                    scanEnabled = true,
                ),
                activePlayerId = "USR_01",
            ),
        )

        render(viewModel)

        composeRule.onNodeWithText(EventDrawUiMapper.REQUIRED_DRAWS_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(EventDrawUiMapper.SCAN_BUTTON_LABEL).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun luckyDrawScreenDisablesScanButtonWhileProcessing() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                commandInFlight = true,
                workflowState = GameplayWorkflowState.EventDrawScanRequired(
                    parentEventId = "EVT_15",
                    actingPlayerId = "USR_01",
                ),
                eventDraw = EventDrawUiState(
                    parentEventId = "EVT_15",
                    parentEventName = "Lucky Draw",
                    actingPlayerId = "USR_01",
                    actingPlayerName = "Nishith",
                    instruction = EventDrawUiMapper.INSTRUCTION,
                    requiredDrawsText = EventDrawUiMapper.REQUIRED_DRAWS_TEXT,
                    scanButtonLabel = EventDrawUiMapper.SCAN_BUTTON_LABEL,
                    scanEnabled = false,
                ),
                activePlayerId = "USR_01",
            ),
        )

        render(viewModel)

        composeRule.onNodeWithText(EventDrawUiMapper.SCAN_BUTTON_LABEL).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun resultScreenHidesTerminationActions() {
        val viewModel = GameViewModel(
            sessionManager = AppTestSupport.sessionManager(),
            definitions = indiaDefinitions,
            transientWorkflow = TransientScanWorkflowHolder(),
            locationWorkflowHolder = LocationWorkflowHolder(),
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
        )
        viewModel.setTestUiState(
            GameUiState(
                loading = false,
                editionId = EditionIds.INDIA,
                workflowState = GameplayWorkflowState.Ready,
                result = GameplayResultUiModel(
                    title = "PURCHASE",
                    primaryMessage = "Solar Energy purchased.",
                ),
            ),
        )

        render(viewModel)

        assertTextDoesNotExist("End Game")
        assertTextDoesNotExist("Abandon Game")
    }
}
