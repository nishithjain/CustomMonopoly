package com.boardbanker.app.navigation

enum class AppDestination(val route: String) {
    Home("home"),
    PlayerSetup("player_setup/{newGame}"),
    PlayerScanner("player_scanner"),
    Game("game"),
    GameScanner("game_scanner"),
    AdvancedBanking("advanced_banking"),
    BankingScanner("banking_scanner"),
    Auction("auction/{propertyId}/{startedByPlayerId}"),
    DebtResolution("debt_resolution"),
    GameOver("game_over"),
    TransactionHistory("transaction_history"),
    GameStatus("game_status"),
    PlayerDetails("player_details/{playerId}"),
    QrScanner("qr_scanner"),
    ResumeGame("resume_game"),
    PersistenceDebug("persistence_debug"),
    ;

    companion object {
        fun playerSetupRoute(newGame: Boolean): String = "player_setup/$newGame"

        fun auctionRoute(propertyId: String, startedByPlayerId: String): String =
            "auction/$propertyId/$startedByPlayerId"

        fun playerDetailsRoute(playerId: String): String = "player_details/$playerId"
    }
}
