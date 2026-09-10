package com.boardbanker.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommonUiIconComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun iconTag(icon: CommonUiIcon): String = "common_ui_icon_${icon.name.lowercase()}"

    @Test
    fun activeGameHubActionsShowMappedIcons() {
        composeRule.setContent {
            BankingQRTheme {
                com.boardbanker.app.ui.screens.game.ActiveGameHubActions(
                    uiState = com.boardbanker.app.ui.screens.game.GameUiState(
                        activePlayerId = "USR_01",
                        activePlayerName = "Nishith",
                        actionAvailability = com.boardbanker.app.ui.screens.game.ActiveGameActionAvailability(
                            scanCardEnabled = true,
                            bankActionsEnabled = true,
                            endTurnEnabled = true,
                            getOutOfJailEnabled = false,
                        ),
                        endTurnSubtitle = "End Nishith's turn",
                        endTurnContentDescription = "End turn",
                    ),
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

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.SCAN_CARD), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.BANK), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.END_TURN), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.END_GAME), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.ABANDON_GAME), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Scan Card").assertIsDisplayed()
    }

    @Test
    fun backActionButtonInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            BankingQRTheme {
                BackActionButton(onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.BACK), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("BACK").performClick()
        assertTrue(clicked)
    }

    @Test
    fun bankingActionBarShowsCheckAndCancelIcons() {
        composeRule.setContent {
            BankingQRTheme {
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("DONE"),
                    onConfirm = {},
                    cancelLabel = BankingActionLabels.cancel(),
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.CHECK), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.CANCEL), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun startGameConfirmUsesStartGameIcon() {
        composeRule.setContent {
            BankingQRTheme {
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("START GAME"),
                    confirmIcon = CommonUiIcon.START_GAME,
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.START_GAME), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("START GAME").assertIsDisplayed()
    }

    @Test
    fun auctionExtraActionShowsAuctionIcon() {
        composeRule.setContent {
            BankingQRTheme {
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("BUY"),
                    onConfirm = {},
                    extraActions = listOf(
                        BankingExtraAction(
                            label = "AUCTION",
                            onClick = {},
                            icon = CommonUiIcon.AUCTION,
                            testTag = "property_auction_button",
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.AUCTION), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("AUCTION").assertIsDisplayed()
        composeRule.onNodeWithText("✓ BUY").assertDoesNotExist()
    }

    @Test
    fun resumeGameButtonShowsResumeGameIcon() {
        var clicked = false
        composeRule.setContent {
            BankingQRTheme {
                CommonFilledActionButton(
                    icon = CommonUiIcon.RESUME_GAME,
                    label = "RESUME GAME",
                    onClick = { clicked = true },
                    testTag = "home_resume_game_button",
                )
            }
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.RESUME_GAME), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("RESUME GAME").performClick()
        assertTrue(clicked)
    }
}
