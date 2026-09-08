package com.boardbanker.app.ui.screens.banking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.player.CommonUiIcon
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import com.boardbanker.app.ui.components.TopBarBackButton
import com.boardbanker.app.ui.components.TopBarIconTitle
import com.boardbanker.app.ui.theme.BankingQRTheme
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameStatusScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(indiaDefinitions)

    private fun buildStatusModel(playerIds: List<String> = listOf("USR_01", "USR_02")): GameStatusUiModel {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, playerIds)
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_02")).session
        return GameStatusPresentation.build(session, indiaDefinitions)
    }

    @Test
    fun eachPlayerRendersInSeparateCard() {
        val uiModel = buildStatusModel()
        composeRule.setContent {
            BankingQRTheme {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithTag(GameStatusTestTags.playerCard("USR_01")).assertIsDisplayed()
        composeRule.onNodeWithTag(GameStatusTestTags.playerCard("USR_02")).assertIsDisplayed()
        composeRule.onNodeWithText("Players · 2").assertIsDisplayed()
    }

    @Test
    fun playerCardShowsTokenNameBalanceAndCounts() {
        val uiModel = buildStatusModel()
        composeRule.setContent {
            BankingQRTheme {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithText("Nishith").assertIsDisplayed()
        composeRule.onNodeWithTag(GameStatusTestTags.balance("USR_01"))
            .assertTextEquals("₹144,000")
        composeRule.onNodeWithTag(GameStatusTestTags.properties("USR_01"))
            .assertTextEquals("1 property")
        composeRule.onNodeWithTag(GameStatusTestTags.energyGrids("USR_01"))
            .assertTextEquals("0 energy grids")
    }

    @Test
    fun onlyActivePlayerShowsCurrentTurnBadge() {
        val uiModel = buildStatusModel()
        composeRule.setContent {
            BankingQRTheme {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithTag(GameStatusTestTags.currentTurnBadge("USR_01")).assertIsDisplayed()
        composeRule.onNodeWithTag(GameStatusTestTags.currentTurnBadge("USR_02")).assertDoesNotExist()
        composeRule.onNodeWithText("Current turn").assertIsDisplayed()
    }

    @Test
    fun jailStatusUsesReadableBadge() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val jailedPlayer = session.players["USR_02"]!!.copy(jailStatus = true)
        session = session.copy(players = session.players + ("USR_02" to jailedPlayer))
        val uiModel = GameStatusPresentation.build(session, indiaDefinitions)

        composeRule.setContent {
            BankingQRTheme {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithTag(GameStatusTestTags.status("USR_02"))
            .assertTextEquals("In jail")
        composeRule.onNodeWithText("Jail: NO").assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarBackNavigationInvokesCallback() {
        var backClicked = false
        val uiModel = buildStatusModel(listOf("USR_01"))

        composeRule.setContent {
            BankingQRTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                TopBarBackButton(
                                    onClick = { backClicked = true },
                                    testTag = GameStatusTestTags.TOP_BAR_BACK,
                                )
                            },
                            title = {
                                TopBarIconTitle(
                                    icon = CommonUiIcon.GAME_STATUS,
                                    title = "Game Status",
                                )
                            },
                        )
                    },
                ) { padding ->
                    GameStatusContent(
                        uiModel = uiModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(GameStatusTestTags.TOP_BAR_BACK).performClick()
        assertTrue(backClicked)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun layoutSupportsSmallScreenWithFourPlayers() {
        val uiModel = buildStatusModel(listOf("USR_01", "USR_02", "USR_03", "USR_04"))
        composeRule.setContent {
            BankingQRTheme {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithText("Players · 4").assertIsDisplayed()
        composeRule.onNodeWithTag(GameStatusTestTags.PLAYER_LIST)
            .performScrollToNode(hasTestTag(GameStatusTestTags.playerCard("USR_04")))
        composeRule.onNodeWithTag(GameStatusTestTags.playerCard("USR_04")).assertIsDisplayed()
    }

    @Test
    fun darkThemeRendersPlayerCards() {
        val uiModel = buildStatusModel(listOf("USR_01", "USR_02"))
        composeRule.setContent {
            BankingQRTheme(darkTheme = true) {
                GameStatusContent(uiModel = uiModel)
            }
        }

        composeRule.onNodeWithTag(GameStatusTestTags.playerCard("USR_01")).assertIsDisplayed()
        composeRule.onNodeWithTag(GameStatusTestTags.playerCard("USR_02")).assertIsDisplayed()
    }
}
