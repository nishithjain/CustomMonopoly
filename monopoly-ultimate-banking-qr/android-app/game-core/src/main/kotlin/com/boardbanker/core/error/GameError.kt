package com.boardbanker.core.error

sealed class GameError {
    data class Validation(val message: String) : GameError()
    data class InvalidState(val message: String) : GameError()
    data class NotFound(val entity: String, val id: String) : GameError()
    data class InsufficientFunds(val playerId: String, val required: Int, val available: Int) : GameError()
    data class DuplicatePlayer(val playerId: String) : GameError()
    data class InvalidPlayerName(val message: String) : GameError()
    data class PlayerNameTooLong(val maxLength: Int) : GameError()
    data class PlayerLimit(val message: String) : GameError()
    data class UndoNotAllowed(val reason: String) : GameError()
    data class AuctionError(val message: String) : GameError()
    data class DebtError(val message: String) : GameError()
    data class EventError(val message: String) : GameError()
    object GameFinished : GameError()
}
