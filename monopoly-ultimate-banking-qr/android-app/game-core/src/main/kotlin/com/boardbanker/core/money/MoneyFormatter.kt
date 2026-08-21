package com.boardbanker.core.money

import com.boardbanker.core.model.BankingValues
import com.boardbanker.core.model.CurrencyDefinition
import com.boardbanker.core.model.GameDefinitions
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {
    fun format(amount: Int, currency: CurrencyDefinition): String {
        val formattedNumber = if (currency.code == "M") {
            amount.toString()
        } else {
            DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(amount)
        }
        return "${currency.symbol}$formattedNumber"
    }

    fun format(amount: Int, bankingValues: BankingValues): String =
        format(amount, bankingValues.currency)

    fun format(amount: Int, definitions: GameDefinitions): String =
        format(amount, definitions.bankingValues)
}
