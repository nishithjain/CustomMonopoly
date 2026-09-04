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

    data class ProcessEnergyGridLanding(
        val playerId: String,
        val energyGridId: String,
    ) : GameCommand()

    data class PurchaseEnergyGrid(
        val playerId: String,
        val energyGridId: String,
    ) : GameCommand()

    data class ApplyEvent(
        val eventId: String,
        val actingPlayerId: String,
        val propertyId: String? = null,
        val targetPlayerId: String? = null,
        val secondPropertyId: String? = null,
        val secondPlayerId: String? = null,
        val fromBoardPosition: Int? = null,
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

    data class PayGoSalary(
        val playerId: String,
        val reason: com.boardbanker.core.model.GoCollectionReason = com.boardbanker.core.model.GoCollectionReason.PASS,
    ) : GameCommand()

    data class PayLocationFee(
        val playerId: String,
        val targetPropertyId: String,
    ) : GameCommand()

    data class SendPlayerToJail(val playerId: String) : GameCommand()

    data class PayJailFee(val playerId: String) : GameCommand()

    data class ReleasePlayerFromJailByPayment(val playerId: String) : GameCommand()

    data class ReleasePlayerFromJailByDoubles(val playerId: String) : GameCommand()

    data class UseGetOutOfJailPass(val playerId: String) : GameCommand()

    data class StartAuction(
        val propertyId: String? = null,
        val energyGridId: String? = null,
        val startedByPlayerId: String,
    ) : GameCommand() {
        init {
            require(propertyId != null || energyGridId != null) {
                "StartAuction requires propertyId or energyGridId"
            }
            require(propertyId == null || energyGridId == null) {
                "StartAuction cannot target both property and energy grid"
            }
        }
    }

    data class PlaceAuctionBid(
        val playerId: String,
        val bidAmount: Int,
    ) : GameCommand()

    object CompleteAuction : GameCommand()

    object CancelAuction : GameCommand()

    data class ResolveDebt(val propertyId: String) : GameCommand()

    data class ResolveDebtWithProperties(
        val propertyIds: List<String> = emptyList(),
        val energyGridIds: List<String> = emptyList(),
    ) : GameCommand()

    object CheckBankruptcy : GameCommand()

    object UndoLastAction : GameCommand()

    data class EndTurn(val playerId: String) : GameCommand()

    data class RollEventDice(
        val eventId: String,
        val actingPlayerId: String,
        val diceResults: List<Int>,
    ) : GameCommand()

    data class ResolvePendingEventDraw(
        val eventId: String,
        val actingPlayerId: String,
    ) : GameCommand()
}
