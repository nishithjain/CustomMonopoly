package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerDashboardActiveEventsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playerCardShowsAppliedEventStatusLine() {
        composeRule.setContent {
            BankingQRTheme {
                ActiveGamePlayerCard(
                    player = PlayerDashboardUi(
                        playerId = "USR_02",
                        playerName = "Aditya",
                        balanceText = "₹66,000",
                        propertyCount = 4,
                        energyGridCount = 0,
                        hasEnergyGridsInEdition = true,
                        assetsSummaryLine = "4 Properties • 0 Energy Grids",
                        activeEventLines = listOf("Event applied: Rent Relief"),
                    ),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Event applied: Rent Relief").assertIsDisplayed()
    }

    @Test
    fun playerCardShowsMultipleAppliedEventLines() {
        composeRule.setContent {
            BankingQRTheme {
                ActiveGamePlayerCard(
                    player = PlayerDashboardUi(
                        playerId = "USR_01",
                        playerName = "Nishith",
                        balanceText = "₹48,000",
                        activeEventLines = listOf(
                            "Events applied:",
                            "• Rent Relief",
                            "• Extra Turn",
                        ),
                    ),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Events applied:").assertIsDisplayed()
        composeRule.onNodeWithText("• Rent Relief").assertIsDisplayed()
        composeRule.onNodeWithText("• Extra Turn").assertIsDisplayed()
    }
}
