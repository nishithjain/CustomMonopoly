package com.boardbanker.app.ui.screens.playerdetails

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Assert.assertTrue
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

    @Test
    fun getOutOfJailChoiceShowsFourActionsWithoutLegacyOptions() {
        var payClicked = false
        var scanClicked = false
        var doublesClicked = false
        var cancelClicked = false

        composeRule.setContent {
            BankingQRTheme {
                GetOutOfJailChoiceContent(
                    playerName = "Player B",
                    jailFeeText = "₹10,000",
                    supportsJailPassScan = true,
                    actionAvailability = PlayerDetailsActionAvailability(
                        collectGoEnabled = false,
                        locationEnabled = false,
                        goToJailEnabled = false,
                        getOutOfJailEnabled = true,
                    ),
                    onPayJailFee = { payClicked = true },
                    onScanJailPass = { scanClicked = true },
                    onReleaseAfterDoubles = { doublesClicked = true },
                    onCancel = { cancelClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_PAY).assertIsDisplayed()
        composeRule.onNodeWithText("Pay ₹10,000").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_SCAN_PASS).assertIsDisplayed()
        composeRule.onNodeWithText("Scan Get Out of Jail Pass").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_DOUBLES_RELEASE).assertIsDisplayed()
        composeRule.onNodeWithText("Release After Doubles").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_CANCEL).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("More Options").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Record Failed Doubles").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Use Jail Pass").fetchSemanticsNodes().isEmpty())

        composeRule.onAllNodesWithTag("common_ui_icon_money_transfer", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue(it.isNotEmpty()) }
        composeRule.onAllNodesWithTag("common_ui_icon_event_card", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue(it.isNotEmpty()) }
        composeRule.onAllNodesWithTag("common_ui_icon_dice", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue(it.isNotEmpty()) }

        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_PAY).performClick()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_SCAN_PASS).performClick()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_DOUBLES_RELEASE).performClick()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_CANCEL).performClick()
        assertTrue(payClicked)
        assertTrue(scanClicked)
        assertTrue(doublesClicked)
        assertTrue(cancelClicked)
    }

    @Test
    fun ukEditionHidesJailPassScanAction() {
        composeRule.setContent {
            BankingQRTheme {
                GetOutOfJailChoiceContent(
                    playerName = "Nishith",
                    jailFeeText = "M100",
                    supportsJailPassScan = false,
                    actionAvailability = PlayerDetailsActionAvailability(
                        collectGoEnabled = false,
                        locationEnabled = false,
                        goToJailEnabled = false,
                        getOutOfJailEnabled = true,
                    ),
                    onPayJailFee = {},
                    onScanJailPass = {},
                    onReleaseAfterDoubles = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_PAY).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_DOUBLES_RELEASE).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerDetailsTestTags.JAIL_CANCEL).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Scan Get Out of Jail Pass").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText(
            "This edition does not include a Get out of Jail Pass Event Card.",
        ).assertIsDisplayed()
    }
}
