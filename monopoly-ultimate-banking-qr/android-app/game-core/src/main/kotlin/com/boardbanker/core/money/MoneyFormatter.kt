package com.boardbanker.core.money

import com.boardbanker.core.model.BankingValues
import com.boardbanker.core.model.CurrencyDefinition
import com.boardbanker.core.model.GameDefinitions

object MoneyFormatter {
    fun format(amount: Int, currency: CurrencyDefinition): String = "${currency.symbol}$amount"

    fun format(amount: Int, bankingValues: BankingValues): String =
        format(amount, bankingValues.currency)

    fun format(amount: Int, definitions: GameDefinitions): String =
        format(amount, definitions.bankingValues)
}
