package com.boardbanker.core.command

sealed class GameCommand {
    data class CreateGame(val gameId: String) : GameCommand()

    data class RegisterPlayer(
        val playerId: String,
        val playerName: String,
    ) : GameCommand()

    data class RenamePlayer(
        val playerId: String,
        val playerName: String,
    ) : GameCommand()

    object StartGame : GameCommand()

    data class ProcessPropertyLanding(
        val playerId: String,
        val propertyId: String,
    ) : GameCommand()

    data class PurchaseProperty(
        val playerId: String,
        val propertyId: String,
    ) : GameCommand()

    data class ApplyEvent(
        val eventId: String,
        val actingPlayerId: String,
        val propertyId: String? = null,
        val targetPlayerId: String? = null,
        val secondPropertyId: String? = null,
        val secondPlayerId: String? = null,
    ) : GameCommand()

    data class EventPropertyChoice(
        val actingPlayerId: String,
        val propertyId: String,
        val choice: EventPropertyChoiceType,
    ) : GameCommand()

    enum class EventPropertyChoiceType {
        BUY,
        AUCTION,
        RAISE_RENT_LEVEL,
    }

    data class PayGoSalary(val playerId: String) : GameCommand()

    data class PayLocationFee(
        val playerId: String,
        val targetPropertyId: String,
    ) : GameCommand()

    data class SendPlayerToJail(val playerId: String) : GameCommand()

    data class PayJailFee(val playerId: String) : GameCommand()

    data class ReleasePlayerFromJailByPayment(val playerId: String) : GameCommand()

    data class ReleasePlayerFromJailByDoubles(val playerId: String) : GameCommand()

    data class StartAuction(
        val propertyId: String,
        val startedByPlayerId: String,
    ) : GameCommand()

    data class PlaceAuctionBid(
        val playerId: String,
        val bidAmount: Int,
    ) : GameCommand()

    object CompleteAuction : GameCommand()

    object CancelAuction : GameCommand()

    data class ResolveDebt(val propertyId: String) : GameCommand()

    data class ResolveDebtWithProperties(val propertyIds: List<String>) : GameCommand()

    object CheckBankruptcy : GameCommand()

    object UndoLastAction : GameCommand()
}
