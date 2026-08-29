package com.boardbanker.core.rules

import kotlin.math.max
import kotlin.math.min

object DebtSettlementCalculator {
    data class SettlementAmounts(
        val outstandingAmount: Int,
        val selectedPropertyValue: Int,
        val propertyValueApplied: Int,
        val changeAmount: Int,
        val remainingDebt: Int,
    ) {
        val isFullyCovered: Boolean get() = remainingDebt == 0
    }

    fun calculate(
        outstandingAmount: Int,
        selectedPropertyValues: List<Int>,
    ): SettlementAmounts {
        val selectedPropertyValue = selectedPropertyValues.sum()
        val propertyValueApplied = min(outstandingAmount, selectedPropertyValue)
        val changeAmount = max(0, selectedPropertyValue - outstandingAmount)
        val remainingDebt = max(0, outstandingAmount - selectedPropertyValue)
        return SettlementAmounts(
            outstandingAmount = outstandingAmount,
            selectedPropertyValue = selectedPropertyValue,
            propertyValueApplied = propertyValueApplied,
            changeAmount = changeAmount,
            remainingDebt = remainingDebt,
        )
    }
}
