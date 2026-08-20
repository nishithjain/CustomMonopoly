package com.boardbanker.app.navigation

object BankingNavigation {
    const val SCANNED_PLAYER_CARD_ID = "banking_scanned_player_card_id"
    const val SCANNED_PROPERTY_CARD_ID = "banking_scanned_property_card_id"
    const val SCAN_CONTEXT = "banking_scan_context"
}

enum class BankingScanContext {
    PLAYER,
    PROPERTY,
}
