package com.boardbanker.core

import com.boardbanker.core.rules.DebtSettlementCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtSettlementCalculatorTest {
    @Test
    fun exactCoverageHasNoChange() {
        val amounts = DebtSettlementCalculator.calculate(
            outstandingAmount = 160,
            selectedPropertyValues = listOf(160),
        )

        assertEquals(160, amounts.propertyValueApplied)
        assertEquals(0, amounts.changeAmount)
        assertEquals(0, amounts.remainingDebt)
    }

    @Test
    fun overpaymentCalculatesChangeOnceForMultipleProperties() {
        val amounts = DebtSettlementCalculator.calculate(
            outstandingAmount = 300,
            selectedPropertyValues = listOf(180, 160),
        )

        assertEquals(300, amounts.propertyValueApplied)
        assertEquals(40, amounts.changeAmount)
        assertEquals(0, amounts.remainingDebt)
    }

    @Test
    fun partialSettlementHasNoChangeAndRemainingDebt() {
        val amounts = DebtSettlementCalculator.calculate(
            outstandingAmount = 300,
            selectedPropertyValues = listOf(180),
        )

        assertEquals(180, amounts.propertyValueApplied)
        assertEquals(0, amounts.changeAmount)
        assertEquals(120, amounts.remainingDebt)
    }

    @Test
    fun remainingDebtNeverNegative() {
        val amounts = DebtSettlementCalculator.calculate(
            outstandingAmount = 160,
            selectedPropertyValues = listOf(180),
        )

        assertEquals(0, amounts.remainingDebt)
        assertEquals(20, amounts.changeAmount)
    }
}
