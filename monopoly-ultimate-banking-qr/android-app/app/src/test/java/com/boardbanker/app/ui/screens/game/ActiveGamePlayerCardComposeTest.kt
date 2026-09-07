package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActiveGamePlayerCardComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activePlayerCardShowsCurrentTurnBadge() {
        composeRule.setContent {
            BankingQRTheme {
                ActiveGamePlayerCard(
                    player = samplePlayer(isActiveTurn = true),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Current Turn").assertIsDisplayed()
        composeRule.onNodeWithTag("active_game_player_card_USR_01").assertIsDisplayed()
    }

    @Test
    fun playerCardShowsBalanceAssetsStatusAndEvents() {
        composeRule.setContent {
            BankingQRTheme {
                ActiveGamePlayerCard(
                    player = samplePlayer(
                        balanceText = "₹48,000",
                        propertyCount = 1,
                        energyGridCount = 0,
                        assetsSummaryLine = "1 Property • 0 Energy Grids",
                        activeEventLines = listOf("Event applied: Rent Relief"),
                    ),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("₹48,000").assertIsDisplayed()
        composeRule.onNodeWithText("1 Property • 0 Energy Grids").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("Event applied: Rent Relief").assertIsDisplayed()
    }

    @Test
    fun inactivePlayerCardDoesNotShowCurrentTurnBadge() {
        composeRule.setContent {
            BankingQRTheme {
                ActiveGamePlayerCard(
                    player = samplePlayer(isActiveTurn = false),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Nishith").assertIsDisplayed()
        composeRule.onNodeWithText("Current Turn").assertDoesNotExist()
    }

    private fun samplePlayer(
        playerId: String = "USR_01",
        playerName: String = "Nishith",
        balanceText: String = "₹48,000",
        propertyCount: Int = 1,
        energyGridCount: Int = 0,
        assetsSummaryLine: String = "1 Property • 0 Energy Grids",
        isActiveTurn: Boolean = false,
        activeEventLines: List<String> = emptyList(),
    ) = PlayerDashboardUi(
        playerId = playerId,
        playerName = playerName,
        balanceText = balanceText,
        propertyCount = propertyCount,
        energyGridCount = energyGridCount,
        hasEnergyGridsInEdition = true,
        isActiveTurn = isActiveTurn,
        statusText = "Active",
        assetsSummaryLine = assetsSummaryLine,
        activeEventLines = activeEventLines,
    )
}
