package com.boardbanker.app.ui.screens.debt

import com.boardbanker.core.rules.DebtSettlementCalculator

data class DebtSettlementSummary(
    val outstandingAmount: Int,
    val selectedPropertyCount: Int,
    val selectedPropertyValue: Int,
    val propertyValueApplied: Int,
    val remainingDue: Int,
    val changeAmount: Int,
    val changeRecipientName: String,
    val changePayerName: String?,
    val showChangeRows: Boolean,
    val propertiesSelectedLabel: String,
    val settleButtonLabel: String,
    val selectionGuidance: String,
    val isSettleEnabled: Boolean,
    val isDebtFullyCovered: Boolean,
) {
    companion object {
        fun compute(
            outstandingAmount: Int,
            selectedPropertyIds: Set<String>,
            properties: List<DebtPropertyOption>,
            debtorName: String,
            creditorPlayerId: String?,
            creditorName: String,
            formatMoney: (Int) -> String,
        ): DebtSettlementSummary {
            val selectedPropertyCount = selectedPropertyIds.size
            val selectedValues = properties
                .asSequence()
                .filter { it.propertyId in selectedPropertyIds }
                .map { it.debtValue }
                .toList()
            val amounts = DebtSettlementCalculator.calculate(outstandingAmount, selectedValues)
            val propertiesSelectedLabel = when (selectedPropertyCount) {
                1 -> "1 property"
                else -> "$selectedPropertyCount properties"
            }
            val settleButtonLabel = when (selectedPropertyCount) {
                1 -> "Settle with selected property"
                else -> "Settle with selected properties"
            }
            val selectionGuidance = if (amounts.remainingDebt == 0) {
                "The selected property value covers the amount due."
            } else {
                "Select properties worth at least ${formatMoney(amounts.remainingDebt)} more."
            }
            val isBankCreditor = creditorPlayerId == null
            val changePayerName = when {
                amounts.changeAmount == 0 -> null
                isBankCreditor -> "Bank"
                else -> creditorName
            }
            return DebtSettlementSummary(
                outstandingAmount = amounts.outstandingAmount,
                selectedPropertyCount = selectedPropertyCount,
                selectedPropertyValue = amounts.selectedPropertyValue,
                propertyValueApplied = amounts.propertyValueApplied,
                remainingDue = amounts.remainingDebt,
                changeAmount = amounts.changeAmount,
                changeRecipientName = debtorName,
                changePayerName = changePayerName,
                showChangeRows = amounts.changeAmount > 0,
                propertiesSelectedLabel = propertiesSelectedLabel,
                settleButtonLabel = settleButtonLabel,
                selectionGuidance = selectionGuidance,
                isSettleEnabled = selectedPropertyCount > 0,
                isDebtFullyCovered = amounts.isFullyCovered,
            )
        }
    }
}

fun DebtResolutionUiState.withSettlementSummary(
    formatMoney: (Int) -> String,
): DebtResolutionUiState {
    val summary = DebtSettlementSummary.compute(
        outstandingAmount = remainingAfterCash,
        selectedPropertyIds = selectedPropertyIds,
        properties = properties,
        debtorName = debtorName,
        creditorPlayerId = creditorPlayerId,
        creditorName = creditorName,
        formatMoney = formatMoney,
    )
    return copy(
        outstandingAmount = summary.outstandingAmount,
        selectedPropertyCount = summary.selectedPropertyCount,
        selectedPropertyValue = summary.selectedPropertyValue,
        remainingDue = summary.remainingDue,
        changeAmount = summary.changeAmount,
        settlementSummary = summary,
    )
}
