package com.boardbanker.app.scanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.app.ui.components.IconLabelRow
import com.boardbanker.app.ui.theme.BankingQRTheme
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecognizedCardSummaryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    private fun iconTag(icon: CommonUiIcon): String = "common_ui_icon_${icon.name.lowercase()}"

    @Composable
    private fun ResolvedCardPreview(
        card: ResolvedCard,
        darkTheme: Boolean = false,
        onAccept: (() -> Unit)? = null,
        onScanAnother: (() -> Unit)? = null,
    ) {
        BankingQRTheme(darkTheme = darkTheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (RecognizedCardSummaryPresentation.isGameCardSummary(card.cardType)) {
                    RecognizedCardSummary(
                        cardType = card.cardType,
                        cardId = card.cardId,
                        displayName = RecognizedCardSummaryPresentation.displayName(card, indiaDefinitions),
                        editionName = indiaDefinitions.edition?.name,
                        propertyColorGroup = RecognizedCardSummaryPresentation.propertyColorGroup(
                            card.cardId,
                            indiaDefinitions,
                        ),
                    )
                }
                onAccept?.let { accept ->
                    Button(onClick = accept, modifier = Modifier.fillMaxWidth()) {
                        IconLabelRow(icon = CommonUiIcon.CHECK, label = "ACCEPT CARD")
                    }
                }
                onScanAnother?.let { scanAnother ->
                    Button(onClick = scanAnother, modifier = Modifier.fillMaxWidth()) {
                        IconLabelRow(icon = CommonUiIcon.SCAN_CARD, label = "SCAN ANOTHER CARD")
                    }
                }
            }
        }
    }

    private fun resolvedCard(cardType: CardType, cardId: String, displayName: String = "fallback") =
        ResolvedCard(
            cardId = cardId,
            cardType = cardType,
            displayName = displayName,
            qrPayload = "payload",
        )

    @Test
    fun eventSummaryUsesSharedComponent() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.EVENT, "EVT_13"))
        }
        assertSharedSummaryWithoutRawEnum()
    }

    @Test
    fun propertySummaryUsesSharedComponent() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.PROPERTY, "PRP_02"))
        }
        assertSharedSummaryWithoutRawEnum()
    }

    @Test
    fun energyGridSummaryUsesSharedComponent() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.ENERGY_GRID, "ENG_01"))
        }
        assertSharedSummaryWithoutRawEnum()
    }

    private fun assertSharedSummaryWithoutRawEnum() {
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithText("Card recognized").assertIsDisplayed()
        composeRule.onNodeWithText("CARD RECOGNIZED").assertIsNotDisplayed()
        composeRule.onNodeWithText("EVENT").assertIsNotDisplayed()
        composeRule.onNodeWithText("PROPERTY").assertIsNotDisplayed()
        composeRule.onNodeWithText("ENERGY_GRID").assertIsNotDisplayed()
    }

    @Test
    fun eventSummaryShowsCorrectMetadata() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.EVENT, "EVT_13"))
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.NAME)
            .assertTextEquals("Local Market Boom")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.TYPE)
            .assertTextEquals("Event")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.CARD_ID)
            .assertTextEquals("EVT_13")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.EDITION)
            .assertTextEquals("India Edition")
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.EVENT_CARD), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun propertySummaryShowsCorrectMetadata() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.PROPERTY, "PRP_02"))
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.NAME)
            .assertTextEquals("[2] Lodhi Garden")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.TYPE)
            .assertTextEquals("Property")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.CARD_ID)
            .assertTextEquals("PRP_02")
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.PROPERTY), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun energyGridSummaryShowsCorrectMetadata() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.ENERGY_GRID, "ENG_01"))
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.NAME)
            .assertTextEquals("[15] Solar Energy")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.TYPE)
            .assertTextEquals("Energy Grid")
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.CARD_ID)
            .assertTextEquals("ENG_01")
        composeRule.onNodeWithTag(iconTag(CommonUiIcon.ENERGY_GRID), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun longCardNameRemainsVisibleWithoutOverlappingTypeLabel() {
        val longName = "A Very Long Event Name That Should Wrap Across Two Lines Without Breaking Layout"
        composeRule.setContent {
            BankingQRTheme {
                RecognizedCardSummary(
                    cardType = CardType.EVENT,
                    cardId = "EVT_99",
                    displayName = longName,
                    editionName = "India Edition",
                )
            }
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.NAME)
            .assertTextEquals(longName)
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.TYPE)
            .assertTextEquals("Event")
            .assertIsDisplayed()
    }

    @Test
    fun acceptAndScanAnotherButtonsRetainCallbacks() {
        var accepted = false
        var scannedAgain = false

        composeRule.setContent {
            ResolvedCardPreview(
                card = resolvedCard(CardType.EVENT, "EVT_13"),
                onAccept = { accepted = true },
                onScanAnother = { scannedAgain = true },
            )
        }

        composeRule.onNodeWithText("ACCEPT CARD").assertIsEnabled().performClick()
        composeRule.onNodeWithText("SCAN ANOTHER CARD").assertIsEnabled().performClick()

        assertTrue(accepted)
        assertTrue(scannedAgain)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun summaryRendersOnSmallScreen() {
        composeRule.setContent {
            ResolvedCardPreview(card = resolvedCard(CardType.PROPERTY, "PRP_02"))
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithText("Card recognized").assertIsDisplayed()
    }

    @Test
    fun summaryRendersInLightTheme() {
        composeRule.setContent {
            ResolvedCardPreview(
                card = resolvedCard(CardType.ENERGY_GRID, "ENG_01"),
                darkTheme = false,
            )
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.SUMMARY).assertIsDisplayed()
    }

    @Test
    fun summaryRendersInDarkTheme() {
        composeRule.setContent {
            ResolvedCardPreview(
                card = resolvedCard(CardType.ENERGY_GRID, "ENG_01"),
                darkTheme = true,
            )
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.SUMMARY).assertIsDisplayed()
    }

    @Test
    fun missingEditionNameOmitsEditionRow() {
        composeRule.setContent {
            BankingQRTheme {
                RecognizedCardSummary(
                    cardType = CardType.EVENT,
                    cardId = "EVT_13",
                    displayName = "Local Market Boom",
                    editionName = null,
                )
            }
        }

        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.EDITION).assertDoesNotExist()
        composeRule.onNodeWithTag(RecognizedCardSummaryTestTags.CARD_ID)
            .assertTextEquals("EVT_13")
    }
}
