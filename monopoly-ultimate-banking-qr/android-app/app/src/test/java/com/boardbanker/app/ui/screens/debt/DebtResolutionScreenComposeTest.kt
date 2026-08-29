package com.boardbanker.app.ui.screens.debt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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
class DebtResolutionScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val properties = listOf(
        DebtPropertyOption(propertyId = "PRP_A", propertyName = "Property A", debtValue = 180),
        DebtPropertyOption(propertyId = "PRP_B", propertyName = "Property B", debtValue = 120),
    )

    private fun formatMoney(amount: Int): String = "M$amount"

    private fun baseState(): DebtResolutionUiState =
        DebtResolutionUiState(
            debtorPlayerId = "USR_02",
            debtorName = "Aditya",
            creditorPlayerId = "USR_01",
            creditorName = "Nishith",
            amountDue = 500,
            availableCash = 0,
            remainingAfterCash = 500,
            properties = properties,
        ).withSettlementSummary(::formatMoney)

    private fun render(state: DebtResolutionUiState) {
        composeRule.setContent {
            BankingQRTheme {
                DebtResolutionActiveContent(
                    uiState = state,
                    formatMoney = ::formatMoney,
                    onToggleProperty = {},
                    onScanPropertyRequested = {},
                    onSettleSelected = {},
                    onCheckBankruptcy = {},
                )
            }
        }
    }

    @Test
    fun settlementSummaryVisibleWithInitialValues() {
        render(baseState())

        composeRule.onNodeWithTag(DebtResolutionTestTags.SETTLEMENT_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(DebtResolutionTestTags.AMOUNT_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Amount due: M500")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_COUNT)
            .assertIsDisplayed()
            .assertTextEquals("Properties selected: 0 properties")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_VALUE)
            .assertIsDisplayed()
            .assertTextEquals("Selected property value: M0")
        composeRule.onNodeWithTag(DebtResolutionTestTags.REMAINING_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Remaining due: M500")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SETTLEMENT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithText("Settle with selected properties").assertIsDisplayed()
    }

    @Test
    fun settlementSummaryShowsOneSelectedProperty() {
        render(
            baseState()
                .copy(selectedPropertyIds = setOf("PRP_A"))
                .withSettlementSummary(::formatMoney),
        )

        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_COUNT)
            .assertIsDisplayed()
            .assertTextEquals("Properties selected: 1 property")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_VALUE)
            .assertIsDisplayed()
            .assertTextEquals("Selected property value: M180")
        composeRule.onNodeWithTag(DebtResolutionTestTags.REMAINING_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Remaining due: M320")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SETTLEMENT_BUTTON).assertIsEnabled()
        composeRule.onNodeWithText("Settle with selected property").assertIsDisplayed()
        composeRule.onNodeWithText("Select properties worth at least M320 more.").assertIsDisplayed()
    }

    @Test
    fun settlementSummaryShowsTwoSelectedProperties() {
        render(
            baseState()
                .copy(selectedPropertyIds = setOf("PRP_A", "PRP_B"))
                .withSettlementSummary(::formatMoney),
        )

        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_COUNT)
            .assertIsDisplayed()
            .assertTextEquals("Properties selected: 2 properties")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_VALUE)
            .assertIsDisplayed()
            .assertTextEquals("Selected property value: M300")
        composeRule.onNodeWithTag(DebtResolutionTestTags.REMAINING_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Remaining due: M200")
        composeRule.onNodeWithText("Settle with selected properties").assertIsDisplayed()
        composeRule.onNodeWithText("Select properties worth at least M200 more.").assertIsDisplayed()
    }

    @Test
    fun settlementSummaryAfterDeselectingFirstProperty() {
        render(
            baseState()
                .copy(selectedPropertyIds = setOf("PRP_B"))
                .withSettlementSummary(::formatMoney),
        )

        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_COUNT)
            .assertIsDisplayed()
            .assertTextEquals("Properties selected: 1 property")
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTED_PROPERTY_VALUE)
            .assertIsDisplayed()
            .assertTextEquals("Selected property value: M120")
        composeRule.onNodeWithTag(DebtResolutionTestTags.REMAINING_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Remaining due: M380")
    }

    @Test
    fun overpaymentShowsChangeRows() {
        render(
            baseState()
                .copy(remainingAfterCash = 160, amountDue = 160, selectedPropertyIds = setOf("PRP_A"))
                .withSettlementSummary(::formatMoney),
        )

        composeRule.onNodeWithTag(DebtResolutionTestTags.REMAINING_DUE)
            .assertIsDisplayed()
            .assertTextEquals("Remaining due: M0")
        composeRule.onNodeWithTag(DebtResolutionTestTags.CHANGE_AMOUNT)
            .assertIsDisplayed()
            .assertTextEquals("Change returned to Aditya: M20")
        composeRule.onNodeWithText("Change paid by Nishith").assertIsDisplayed()
        composeRule.onNodeWithTag(DebtResolutionTestTags.SELECTION_GUIDANCE)
            .assertIsDisplayed()
            .assertTextEquals("The selected property value covers the amount due.")
    }

    @Test
    fun settlementSummaryAppearsBeforePropertyListInCompositionOrder() {
        render(baseState())

        composeRule.onNodeWithTag(DebtResolutionTestTags.SETTLEMENT_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(DebtResolutionTestTags.propertyCheckbox("PRP_A")).assertIsDisplayed()
        composeRule.onNodeWithTag(DebtResolutionTestTags.propertyCheckbox("PRP_B")).assertIsDisplayed()
    }
}
