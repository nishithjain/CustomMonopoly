package com.boardbanker.app.ui.components

import com.boardbanker.app.player.CommonUiIcon

object GameplayResultIcons {
    fun iconForTitle(title: String): CommonUiIcon? = when {
        title.contains("PURCHASE", ignoreCase = true) -> CommonUiIcon.BUY_PROPERTY
        title.contains("ENERGY GRID", ignoreCase = true) -> CommonUiIcon.ENERGY_GRID
        title.contains("RENT", ignoreCase = true) -> CommonUiIcon.RENT
        title.contains("AUCTION", ignoreCase = true) -> CommonUiIcon.AUCTION
        title.contains("DEBT", ignoreCase = true) || title.contains("BANKRUPT", ignoreCase = true) -> CommonUiIcon.DEBT
        title.contains("COLOR SET", ignoreCase = true) -> CommonUiIcon.COLOR_SET_COMPLETE
        title.contains("LUCKY BREAK", ignoreCase = true) || title.contains("LUCKY DRAW", ignoreCase = true) -> CommonUiIcon.LUCKY_DRAW
        title == "ERROR" || title.contains("CANNOT BE UNDONE", ignoreCase = true) -> CommonUiIcon.ERROR
        title.contains("UNDO", ignoreCase = true) -> CommonUiIcon.UNDO_LAST_ACTION
        else -> null
    }
}
