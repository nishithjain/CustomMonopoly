package com.boardbanker.core.event

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EventInstructionFormatterTest {
    private val india = TestFixtures.loadEdition(EditionIds.INDIA)

    @Test
    fun hospitalExpense_resolvesAmountPlaceholder() {
        val event = india.events["EVT_05"]!!
        val formatted = EventInstructionFormatter.formatDescription(event, india)
        assertEquals(
            "Pay ${MoneyFormatter.format(10000, india)} to the bank.",
            formatted,
        )
        assertFalse(formatted.contains("{amount}"))
    }

    @Test
    fun advanceToGo_resolvesGoSalaryPlaceholder() {
        val event = india.events["EVT_01"]!!
        val formatted = EventInstructionFormatter.formatDescription(event, india)
        assertEquals(
            "Move directly to GO and collect ${MoneyFormatter.format(india.bankingValues.goSalary, india)} once.",
            formatted,
        )
        assertFalse(formatted.contains("{goSalary}"))
    }

    @Test
    fun festivalContribution_resolvesPerPlayerAmount() {
        val event = india.events["EVT_06"]!!
        val formatted = EventInstructionFormatter.formatDescription(event, india)
        assertEquals(
            "Pay ${MoneyFormatter.format(5000, india)} to each other player.",
            formatted,
        )
        assertFalse(formatted.contains("{amountPerOtherPlayer}"))
    }

    @Test
    fun changingConfiguredAmount_updatesDisplayedInstruction() {
        val event = india.events["EVT_05"]!!
        val action = event.actions.first().copy(amount = 25000)
        val customized = event.copy(actions = listOf(action))
        val formatted = EventInstructionFormatter.formatDescription(customized, india)
        assertEquals(
            "Pay ${MoneyFormatter.format(25000, india)} to the bank.",
            formatted,
        )
    }
}
