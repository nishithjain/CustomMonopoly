package com.boardbanker.app.ui.screens.playerdetails

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
class PlayerDetailsScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleProperty = OwnedPropertyUi(
        propertyId = "PRP_03",
        propertyName = "[3] Cubbon Park",
        colorGroup = "BROWN",
        colorGroupLabel = "Brown",
        rentLevel = 4,
        maxRentLevel = 5,
        currentRentText = "₹37,000",
        purchasePriceText = "₹6,000",
    )

    private val sampleEnergyGrid = OwnedEnergyGridUi(
        energyGridId = "ENG_01",
        energyGridName = "Solar Energy",
        boardPosition = 15,
        boardPositionLabel = "Board 15",
        categoryLabel = "Energy Grid",
        currentRentText = "₹5,000",
        purchasePriceText = "₹20,000",
        rentTierText = "Rent with 1 owned grid",
    )

    private val sampleState = PlayerDetailsUiState(
        editionId = "india",
        playerId = "USR_02",
        playerName = "Player B",
        balanceText = "₹91,000",
        playerStatusText = "Active",
        propertyCount = 4,
        energyGridCount = 1,
        totalAssetCount = 5,
        isActiveTurn = true,
        hasEnergyGridsInEdition = true,
        ownedProperties = listOf(sampleProperty),
        ownedEnergyGrids = listOf(sampleEnergyGrid),
    )

    @Test
    fun summaryCardShowsCountsAndCurrentTurn() {
        composeRule.setContent {
            BankingQRTheme {
                PlayerDetailsAssetsContent(
                    uiState = sampleState,
                    onPropertySelected = {},
                    onEnergyGridSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("player_details_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("Current Turn").assertIsDisplayed()
        composeRule.onNodeWithText("₹91,000").assertIsDisplayed()
        composeRule.onNodeWithTag("summary_property_count").assertIsDisplayed()
        composeRule.onNodeWithTag("summary_energy_grid_count").assertIsDisplayed()
    }

    @Test
    fun propertyAndEnergyGridRenderInIndividualCards() {
        composeRule.setContent {
            BankingQRTheme {
                PlayerDetailsAssetsContent(
                    uiState = sampleState,
                    onPropertySelected = {},
                    onEnergyGridSelected = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("owned_property_card_PRP_03").assertIsDisplayed()
        composeRule.onNodeWithTag("owned_energy_grid_card_ENG_01").assertExists()
    }
}
