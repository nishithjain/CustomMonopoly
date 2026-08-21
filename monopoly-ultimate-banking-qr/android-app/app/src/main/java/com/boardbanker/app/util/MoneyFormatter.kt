package com.boardbanker.app.util

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.money.MoneyFormatter

fun formatMoney(amount: Int, definitions: GameDefinitions): String =
    MoneyFormatter.format(amount, definitions)
