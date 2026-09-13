package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.core.model.TransactionType

internal object HistoryEntryIcons {
    fun forTransactionType(
        type: TransactionType,
        eventName: String? = null,
        subtitle: String? = null,
    ): CommonUiIcon? = when (type) {
        TransactionType.PROPERTY_PURCHASE -> CommonUiIcon.PROPERTY
        TransactionType.AUCTION_WIN,
        TransactionType.AUCTION_PURCHASE,
        -> CommonUiIcon.AUCTION
        TransactionType.COLOR_SET_COMPLETION_BONUS -> CommonUiIcon.COLOR_SET_COMPLETE
        TransactionType.RENT_PAYMENT -> CommonUiIcon.RENT
        TransactionType.RENT_WAIVED -> CommonUiIcon.RENT
        TransactionType.PROPERTY_RENT_LEVEL_CHANGE -> rentLevelIcon(subtitle ?: "")
        TransactionType.ENERGY_GRID_PURCHASE,
        TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE,
        -> CommonUiIcon.ENERGY_GRID
        TransactionType.BANKRUPTCY -> CommonUiIcon.DEBT
        TransactionType.RENT_DEBT_SETTLED -> CommonUiIcon.RENT
        TransactionType.EVENT_PLAYER_TRANSFER,
        TransactionType.EVENT_MULTI_PLAYER_TRANSFER,
        -> CommonUiIcon.MONEY_TRANSFER
        TransactionType.UNDO -> CommonUiIcon.UNDO_LAST_ACTION
        TransactionType.PROPERTY_OWNERSHIP_CHANGE -> CommonUiIcon.PROPERTY
        TransactionType.EVENT_APPLIED -> eventIcon(eventName)
        TransactionType.LOCATION_FEE -> CommonUiIcon.LOCATION
        TransactionType.JAIL_STATUS_CHANGE,
        TransactionType.JAIL_PASS_USED,
        -> CommonUiIcon.JAIL
        TransactionType.TURN_ADVANCED -> CommonUiIcon.CURRENT_TURN
        TransactionType.BANK_CREDIT,
        TransactionType.BANK_DEBIT,
        -> CommonUiIcon.BANK
        else -> null
    }

    fun forDetail(detail: HistoryDetail): CommonUiIcon? = when (detail) {
        is HistoryDetail.RentLevelChange -> rentLevelIcon(detail.levelChangeText)
        else -> null
    }

    private fun eventIcon(eventName: String?): CommonUiIcon {
        val name = eventName?.lowercase() ?: return CommonUiIcon.EVENT_CARD
        return when {
            name.contains("lucky draw") -> CommonUiIcon.LUCKY_DRAW
            name.contains("birthday") || name.contains("festival") || name.contains("contribution") ->
                CommonUiIcon.MONEY_TRANSFER
            else -> CommonUiIcon.EVENT_CARD
        }
    }

    private fun rentLevelIcon(text: String): CommonUiIcon {
        val normalized = text.lowercase()
        return when {
            normalized.contains("decrease") || normalized.contains("lower") -> CommonUiIcon.RENT_DECREASE
            normalized.contains("increase") || normalized.contains("raise") -> CommonUiIcon.RENT_INCREASE
            else -> CommonUiIcon.RENT
        }
    }
}
