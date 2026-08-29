package com.boardbanker.app.ui.screens.debt

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtSettlementSummaryTest {
    private val properties = listOf(
        DebtPropertyOption(propertyId = "PRP_10", propertyName = "Marlborough Street", debtValue = 180),
        DebtPropertyOption(propertyId = "PRP_11", propertyName = "Vine Street", debtValue = 200),
        DebtPropertyOption(propertyId = "PRP_12", propertyName = "Strand", debtValue = 220),
    )

    private fun formatUk(amount: Int): String = formatMoney(amount, AppTestSupport.definitions)

    private fun computeSummary(
        outstandingAmount: Int,
        selectedPropertyIds: Set<String> = emptySet(),
        debtorName: String = "Aditya",
        creditorPlayerId: String? = "USR_01",
        creditorName: String = "Nishith",
    ): DebtSettlementSummary =
        DebtSettlementSummary.compute(
            outstandingAmount = outstandingAmount,
            selectedPropertyIds = selectedPropertyIds,
            properties = properties,
            debtorName = debtorName,
            creditorPlayerId = creditorPlayerId,
            creditorName = creditorName,
            formatMoney = ::formatUk,
        )

    @Test
    fun initialSummaryUsesZeroSelectionAndFullOutstandingDebt() {
        val summary = computeSummary(outstandingAmount = 500)

        assertEquals(500, summary.outstandingAmount)
        assertEquals(0, summary.selectedPropertyCount)
        assertEquals(0, summary.selectedPropertyValue)
        assertEquals(500, summary.remainingDue)
        assertEquals("0 properties", summary.propertiesSelectedLabel)
        assertEquals("Settle with selected properties", summary.settleButtonLabel)
        assertFalse(summary.isSettleEnabled)
        assertEquals("Select properties worth at least M500 more.", summary.selectionGuidance)
        assertFalse(summary.showChangeRows)
    }

    @Test
    fun selectingOnePropertyUpdatesCountValueAndRemainingDue() {
        val summary = computeSummary(outstandingAmount = 500, selectedPropertyIds = setOf("PRP_11"))

        assertEquals(1, summary.selectedPropertyCount)
        assertEquals(200, summary.selectedPropertyValue)
        assertEquals(300, summary.remainingDue)
        assertEquals("1 property", summary.propertiesSelectedLabel)
        assertEquals("Settle with selected property", summary.settleButtonLabel)
        assertTrue(summary.isSettleEnabled)
        assertEquals("Select properties worth at least M300 more.", summary.selectionGuidance)
    }

    @Test
    fun reportedScenarioShowsTwentyChangeFromCreditor() {
        val summary = computeSummary(
            outstandingAmount = 160,
            selectedPropertyIds = setOf("PRP_10"),
            debtorName = "Aditya",
            creditorName = "Nishith",
        )

        assertEquals(0, summary.remainingDue)
        assertEquals(20, summary.changeAmount)
        assertTrue(summary.showChangeRows)
        assertEquals("Aditya", summary.changeRecipientName)
        assertEquals("Nishith", summary.changePayerName)
    }

    @Test
    fun selectingMultiplePropertiesSumsSettlementValues() {
        val summary = computeSummary(outstandingAmount = 500, selectedPropertyIds = setOf("PRP_10", "PRP_11"))

        assertEquals(2, summary.selectedPropertyCount)
        assertEquals(380, summary.selectedPropertyValue)
        assertEquals(120, summary.remainingDue)
        assertEquals("2 properties", summary.propertiesSelectedLabel)
        assertEquals("Settle with selected properties", summary.settleButtonLabel)
        assertEquals("Select properties worth at least M120 more.", summary.selectionGuidance)
    }

    @Test
    fun multiplePropertiesCalculateSingleChangeAmount() {
        val summary = computeSummary(outstandingAmount = 300, selectedPropertyIds = setOf("PRP_10", "PRP_11"))

        assertEquals(80, summary.changeAmount)
        assertEquals(0, summary.remainingDue)
        assertEquals("Nishith", summary.changePayerName)
    }

    @Test
    fun deselectingPropertyReducesCountAndValue() {
        val summary = computeSummary(outstandingAmount = 500, selectedPropertyIds = setOf("PRP_11"))

        assertEquals(1, summary.selectedPropertyCount)
        assertEquals(200, summary.selectedPropertyValue)
        assertEquals(300, summary.remainingDue)
    }

    @Test
    fun samePropertyCannotBeCountedTwice() {
        val summary = computeSummary(outstandingAmount = 500, selectedPropertyIds = setOf("PRP_11"))

        assertEquals(1, summary.selectedPropertyCount)
        assertEquals(200, summary.selectedPropertyValue)
    }

    @Test
    fun remainingDueNeverBecomesNegative() {
        val summary = computeSummary(
            outstandingAmount = 500,
            selectedPropertyIds = setOf("PRP_10", "PRP_11", "PRP_12"),
        )

        assertEquals(600, summary.selectedPropertyValue)
        assertEquals(0, summary.remainingDue)
        assertTrue(summary.isDebtFullyCovered)
        assertEquals("The selected property value covers the amount due.", summary.selectionGuidance)
    }

    @Test
    fun exactCoverageShowsZeroRemainingDueAndCoveredMessage() {
        val summary = computeSummary(outstandingAmount = 380, selectedPropertyIds = setOf("PRP_10", "PRP_11"))

        assertEquals(0, summary.remainingDue)
        assertEquals(0, summary.changeAmount)
        assertFalse(summary.showChangeRows)
        assertEquals("The selected property value covers the amount due.", summary.selectionGuidance)
    }

    @Test
    fun uiStateSummaryMatchesEngineOutstandingDebtAndPurchasePriceValues() {
        val state = DebtResolutionUiState(
            remainingAfterCash = 500,
            selectedPropertyIds = setOf("PRP_11"),
            properties = properties,
            debtorName = "Aditya",
            creditorPlayerId = "USR_01",
            creditorName = "Nishith",
        ).withSettlementSummary(::formatUk)

        assertEquals(500, state.outstandingAmount)
        assertEquals(200, state.selectedPropertyValue)
        assertEquals(300, state.remainingDue)
        assertEquals(
            AppTestSupport.definitions.properties["PRP_11"]!!.purchasePrice,
            state.selectedPropertyValue,
        )
    }

    @Test
    fun indiaEditionUsesEditionAwareCurrencyFormatting() {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("common/card_registry.json").toFile().exists() }
        val indiaDefinitions = EditionRepository(FileEditionFileSource(dataDir)).load(EditionIds.INDIA)
        val summary = DebtSettlementSummary.compute(
            outstandingAmount = 200,
            selectedPropertyIds = emptySet(),
            properties = listOf(
                DebtPropertyOption("PRP_01", "Test", 200),
            ),
            debtorName = "Player",
            creditorPlayerId = null,
            creditorName = "Bank",
            formatMoney = { amount -> formatMoney(amount, indiaDefinitions) },
        )

        assertEquals("Select properties worth at least ₹200 more.", summary.selectionGuidance)
    }
}
