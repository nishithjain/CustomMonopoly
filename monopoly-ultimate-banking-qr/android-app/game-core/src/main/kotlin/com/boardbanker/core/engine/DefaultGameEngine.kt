package com.boardbanker.core.engine

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.error.GameError
import com.boardbanker.core.event.EventEngine
import com.boardbanker.core.model.AuctionState
import com.boardbanker.core.model.ColorGroupState
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.BankruptcyRules
import com.boardbanker.core.rules.ColorSetRules
import com.boardbanker.core.rules.DebtRules
import com.boardbanker.core.rules.GoRules
import com.boardbanker.core.rules.JailRules
import com.boardbanker.core.rules.PropertyRules
import com.boardbanker.core.rules.RentRules
import com.boardbanker.core.rules.UndoSupport
import com.boardbanker.core.rules.WinnerCalculator
import com.boardbanker.core.transaction.TransactionFactory
import com.boardbanker.core.validation.PlayerNameRules

class DefaultGameEngine(
    private val definitions: GameDefinitions,
) : GameEngine {
    private val transactionFactory = TransactionFactory()
    private val colorSetRules = ColorSetRules(definitions, transactionFactory)
    private val propertyRules = PropertyRules(definitions, colorSetRules, transactionFactory)
    private val winnerCalculator = WinnerCalculator(definitions)
    private val bankruptcyRules = BankruptcyRules(definitions, transactionFactory, winnerCalculator)
    private val debtRules = DebtRules(definitions, transactionFactory, bankruptcyRules)
    private val rentRules = RentRules(definitions, transactionFactory, debtRules)
    private val goRules = GoRules(definitions, transactionFactory)
    private val jailRules = JailRules(definitions, transactionFactory)
    private val undoSupport = UndoSupport(definitions, transactionFactory)
    private val eventEngine = EventEngine(definitions, transactionFactory, jailRules, debtRules)

    private val rules = definitions.rulesConfig
    private val banking = definitions.bankingValues

    override fun process(session: GameSession, command: GameCommand): GameResult {
        return when (command) {
            is GameCommand.CreateGame -> handleCreateGame(command)
            is GameCommand.RegisterPlayer -> handleRegisterPlayer(session, command)
            is GameCommand.RenamePlayer -> handleRenamePlayer(session, command)
            is GameCommand.StartGame -> handleStartGame(session)
            is GameCommand.ProcessPropertyLanding -> handlePropertyLanding(session, command)
            is GameCommand.PurchaseProperty -> handlePurchase(session, command)
            is GameCommand.ApplyEvent -> handleApplyEvent(session, command)
            is GameCommand.EventPropertyChoice -> handleEventPropertyChoice(session, command)
            is GameCommand.PayGoSalary -> handlePayGo(session, command)
            is GameCommand.PayLocationFee -> handleLocationFee(session, command)
            is GameCommand.SendPlayerToJail -> handleSendToJail(session, command)
            is GameCommand.PayJailFee -> handlePayJailFee(session, command)
            is GameCommand.ReleasePlayerFromJailByPayment -> handlePayJailFee(session, GameCommand.PayJailFee(command.playerId))
            is GameCommand.ReleasePlayerFromJailByDoubles -> handleReleaseByDoubles(session, command)
            is GameCommand.StartAuction -> handleStartAuction(session, command)
            is GameCommand.PlaceAuctionBid -> handlePlaceBid(session, command)
            is GameCommand.CompleteAuction -> handleCompleteAuction(session)
            is GameCommand.CancelAuction -> handleCancelAuction(session)
            is GameCommand.ResolveDebt -> handleResolveDebt(session, command)
            is GameCommand.ResolveDebtWithProperties -> handleResolveDebtWithProperties(session, command)
            is GameCommand.CheckBankruptcy -> handleCheckBankruptcy(session)
            is GameCommand.UndoLastAction -> handleUndo(session)
        }
    }

    private fun handleCreateGame(command: GameCommand.CreateGame): GameResult {
        val session = GameSession(
            gameId = command.gameId,
            editionId = definitions.editionId,
            status = GameStatus.SETUP,
        )
        return GameResult(session)
    }

    private fun handleRegisterPlayer(
        session: GameSession,
        command: GameCommand.RegisterPlayer,
    ): GameResult {
        if (session.status != GameStatus.SETUP) {
            return reject(session, GameError.InvalidState("Cannot register players after game started"))
        }
        if (!definitions.players.containsKey(command.playerId)) {
            return reject(session, GameError.NotFound("Player", command.playerId))
        }
        if (session.players.containsKey(command.playerId)) {
            return reject(session, GameError.DuplicatePlayer(command.playerId))
        }
        if (session.players.size >= rules.maximumPlayers) {
            return reject(session, GameError.PlayerLimit("Maximum ${rules.maximumPlayers} players"))
        }
        val nameValidation = PlayerNameRules.validate(command.playerName)
        if (nameValidation is PlayerNameRules.ValidationResult.Invalid) {
            return reject(session, nameValidation.error)
        }
        val playerName = (nameValidation as PlayerNameRules.ValidationResult.Valid).name
        val updated = session.copy(
            players = session.players + (
                command.playerId to PlayerState(
                    playerId = command.playerId,
                    playerName = playerName,
                    balance = banking.startingBalance,
                )
            ),
        )
        return GameResult(updated)
    }

    private fun handleRenamePlayer(
        session: GameSession,
        command: GameCommand.RenamePlayer,
    ): GameResult {
        if (session.status != GameStatus.SETUP) {
            return reject(session, GameError.InvalidState("Cannot rename players after game started"))
        }
        if (!session.players.containsKey(command.playerId)) {
            return reject(session, GameError.NotFound("Player", command.playerId))
        }
        val nameValidation = PlayerNameRules.validate(command.playerName)
        if (nameValidation is PlayerNameRules.ValidationResult.Invalid) {
            return reject(session, nameValidation.error)
        }
        val playerName = (nameValidation as PlayerNameRules.ValidationResult.Valid).name
        val player = session.players[command.playerId]!!
        val updated = session.copy(
            players = session.players + (command.playerId to player.copy(playerName = playerName)),
        )
        return GameResult(updated)
    }

    private fun handleStartGame(session: GameSession): GameResult {
        if (session.status != GameStatus.SETUP) {
            return reject(session, GameError.InvalidState("Game already started"))
        }
        if (session.players.size < rules.minimumPlayers) {
            return reject(session, GameError.Validation("Need at least ${rules.minimumPlayers} players"))
        }
        val properties = definitions.properties.values.associate { def ->
            def.propertyId to PropertyState(
                propertyId = def.propertyId,
                ownerPlayerId = null,
                currentRentLevel = def.initialRentLevel,
            )
        }
        val colorGroups = definitions.boardRelationships.colorGroups.keys.associateWith { group ->
            ColorGroupState(colorGroup = group)
        }
        val players = session.players.mapValues { (_, player) ->
            player.copy(balance = banking.startingBalance, active = true, bankrupt = false, jailStatus = false)
        }
        var updated = session.copy(
            status = GameStatus.ACTIVE,
            players = players,
            properties = properties,
            colorGroups = colorGroups,
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updated,
            type = TransactionType.GAME_START,
        )
        return GameResult(sessionAfter, transactions = listOf(tx))
    }

    private fun handlePropertyLanding(
        session: GameSession,
        command: GameCommand.ProcessPropertyLanding,
    ): GameResult {
        if (session.status != GameStatus.ACTIVE) {
            return reject(session, GameError.GameFinished)
        }
        val propertyState = session.properties[command.propertyId]
            ?: return reject(session, GameError.NotFound("Property", command.propertyId))
        when (propertyState.ownerPlayerId) {
            null -> return GameResult(
                session = session,
                outcome = GameOutcome.PENDING_ACTION,
                pendingMessage = "Property unowned: choose BUY or AUCTION",
            )
            command.playerId -> {
                val result = propertyRules.ownerLandsOnOwnProperty(
                    session, command.playerId, command.propertyId,
                )
                if (!result.isSuccess) {
                    return reject(session, GameError.Validation(result.error!!))
                }
                return GameResult(result.session!!, transactions = result.transactions)
            }
            else -> {
                val result = rentRules.processVisitorRent(
                    session, command.playerId, command.propertyId,
                )
                if (!result.isSuccess) {
                    return reject(session, GameError.Validation(result.error!!))
                }
                val outcome = if (result.session!!.debtResolution != null) {
                    GameOutcome.DEBT_RESOLUTION_REQUIRED
                } else {
                    GameOutcome.SUCCESS
                }
                return GameResult(result.session, outcome = outcome, transactions = result.transactions)
            }
        }
    }

    private fun handlePurchase(
        session: GameSession,
        command: GameCommand.PurchaseProperty,
    ): GameResult {
        val propertyDef = definitions.properties[command.propertyId]
            ?: return reject(session, GameError.NotFound("Property", command.propertyId))
        val buyer = session.players[command.playerId]
            ?: return reject(session, GameError.NotFound("Player", command.playerId))
        if (buyer.balance < propertyDef.purchasePrice) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = command.playerId,
                creditorId = EntityRef.BANK,
                amount = propertyDef.purchasePrice,
                reason = DebtReason.PURCHASE,
                propertyId = command.propertyId,
            )
            if (!debtResult.isSuccess) {
                return reject(session, GameError.Validation(debtResult.error!!))
            }
            return GameResult(
                session = debtResult.session!!,
                outcome = GameOutcome.DEBT_RESOLUTION_REQUIRED,
                transactions = debtResult.transactions,
            )
        }
        val result = propertyRules.purchaseProperty(session, command.playerId, command.propertyId)
        if (!result.isSuccess) {
            return reject(session, GameError.Validation(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun handleApplyEvent(
        session: GameSession,
        command: GameCommand.ApplyEvent,
    ): GameResult {
        val result = eventEngine.apply(
            session = session,
            eventId = command.eventId,
            actingPlayerId = command.actingPlayerId,
            propertyId = command.propertyId,
            targetPlayerId = command.targetPlayerId,
            secondPropertyId = command.secondPropertyId,
            secondPlayerId = command.secondPlayerId,
        )
        if (!result.isSuccess) {
            return reject(session, GameError.EventError(result.error!!))
        }
        val outcome = when {
            result.needsDebtResolution -> GameOutcome.DEBT_RESOLUTION_REQUIRED
            result.pendingMessage != null -> GameOutcome.PENDING_ACTION
            else -> GameOutcome.SUCCESS
        }
        return GameResult(
            session = result.session!!,
            outcome = outcome,
            transactions = result.transactions,
            physicalActions = result.physicalActions,
            pendingMessage = result.pendingMessage,
        )
    }

    private fun handleEventPropertyChoice(
        session: GameSession,
        command: GameCommand.EventPropertyChoice,
    ): GameResult {
        val pending = session.pendingEventChoice
            ?: return reject(session, GameError.InvalidState("No pending event choice"))
        when (command.choice) {
            GameCommand.EventPropertyChoiceType.BUY -> {
                val purchase = propertyRules.purchaseProperty(
                    session.copy(pendingEventChoice = null),
                    command.actingPlayerId,
                    command.propertyId,
                )
                if (!purchase.isSuccess) {
                    return reject(session, GameError.Validation(purchase.error!!))
                }
                return GameResult(purchase.session!!, transactions = purchase.transactions)
            }
            GameCommand.EventPropertyChoiceType.AUCTION -> {
                val start = handleStartAuction(
                    session.copy(pendingEventChoice = null),
                    GameCommand.StartAuction(command.propertyId, command.actingPlayerId),
                )
                return start
            }
            GameCommand.EventPropertyChoiceType.RAISE_RENT_LEVEL -> {
                val propertyState = session.properties[command.propertyId]
                if (propertyState?.ownerPlayerId != command.actingPlayerId) {
                    return reject(session, GameError.Validation("Must own property to raise rent"))
                }
                val result = propertyRules.ownerLandsOnOwnProperty(
                    session.copy(pendingEventChoice = null),
                    command.actingPlayerId,
                    command.propertyId,
                )
                if (!result.isSuccess) {
                    return reject(session, GameError.Validation(result.error!!))
                }
                return GameResult(result.session!!, transactions = result.transactions)
            }
        }
    }

    private fun handlePayGo(
        session: GameSession,
        command: GameCommand.PayGoSalary,
    ): GameResult {
        val result = goRules.payGoSalary(session, command.playerId)
        if (!result.isSuccess) {
            return reject(session, GameError.Validation(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun handleLocationFee(
        session: GameSession,
        command: GameCommand.PayLocationFee,
    ): GameResult {
        val player = session.players[command.playerId]
            ?: return reject(session, GameError.NotFound("Player", command.playerId))
        val fee = banking.locationFee
        if (player.balance < fee) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = command.playerId,
                creditorId = EntityRef.BANK,
                amount = fee,
                reason = DebtReason.LOCATION,
                propertyId = command.targetPropertyId,
            )
            if (!debtResult.isSuccess) {
                return reject(session, GameError.Validation(debtResult.error!!))
            }
            return GameResult(
                session = debtResult.session!!,
                outcome = GameOutcome.DEBT_RESOLUTION_REQUIRED,
                transactions = debtResult.transactions,
            )
        }
        val updatedPlayer = player.copy(balance = player.balance - fee)
        var updated = session.copy(players = session.players + (command.playerId to updatedPlayer))
        val (feeTx, sessionAfterFee) = transactionFactory.create(
            session = updated,
            type = TransactionType.LOCATION_FEE,
            fromEntity = command.playerId,
            toEntity = EntityRef.BANK,
            playerId = command.playerId,
            amount = fee,
            reversible = true,
        )
        updated = sessionAfterFee.copy(undoSnapshot = session.snapshot())

        val propertyState = updated.properties[command.targetPropertyId]
        if (propertyState == null) {
            return GameResult(updated, transactions = listOf(feeTx))
        }
        when (propertyState.ownerPlayerId) {
            null -> {
                return GameResult(
                    updated,
                    transactions = listOf(feeTx),
                    outcome = GameOutcome.PENDING_ACTION,
                    pendingMessage = "Purchase available for ${command.targetPropertyId}",
                )
            }
            command.playerId -> {
                val landing = propertyRules.ownerLandsOnOwnProperty(
                    updated,
                    command.playerId,
                    command.targetPropertyId,
                )
                return GameResult(
                    landing.session ?: updated,
                    transactions = listOf(feeTx) + landing.transactions,
                )
            }
            else -> {
                val rent = rentRules.processVisitorRent(
                    updated,
                    command.playerId,
                    command.targetPropertyId,
                )
                return GameResult(
                    rent.session ?: updated,
                    transactions = listOf(feeTx) + rent.transactions,
                )
            }
        }
    }

    private fun handleSendToJail(
        session: GameSession,
        command: GameCommand.SendPlayerToJail,
    ): GameResult {
        val result = jailRules.sendToJail(session, command.playerId)
        if (!result.isSuccess) {
            return reject(session, GameError.Validation(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun handlePayJailFee(
        session: GameSession,
        command: GameCommand.PayJailFee,
    ): GameResult {
        val result = jailRules.payJailFee(session, command.playerId)
        if (result.needsDebtResolution) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = command.playerId,
                creditorId = EntityRef.BANK,
                amount = result.debtAmount,
                reason = DebtReason.JAIL,
            )
            if (!debtResult.isSuccess) {
                return reject(session, GameError.Validation(debtResult.error!!))
            }
            return GameResult(
                session = debtResult.session!!,
                outcome = GameOutcome.DEBT_RESOLUTION_REQUIRED,
                transactions = debtResult.transactions,
            )
        }
        if (!result.isSuccess) {
            return reject(session, GameError.Validation(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun handleReleaseByDoubles(
        session: GameSession,
        command: GameCommand.ReleasePlayerFromJailByDoubles,
    ): GameResult {
        val result = jailRules.releaseByDoubles(session, command.playerId)
        if (!result.isSuccess) {
            return reject(session, GameError.Validation(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun handleStartAuction(
        session: GameSession,
        command: GameCommand.StartAuction,
    ): GameResult {
        val propertyState = session.properties[command.propertyId]
            ?: return reject(session, GameError.NotFound("Property", command.propertyId))
        if (propertyState.ownerPlayerId != null) {
            return reject(session, GameError.AuctionError("Property is already owned"))
        }
        if (session.auction != null) {
            return reject(session, GameError.AuctionError("Auction already in progress"))
        }
        val updated = session.copy(
            auction = AuctionState(
                propertyId = command.propertyId,
                startedByPlayerId = command.startedByPlayerId,
            ),
        )
        return GameResult(
            updated,
            outcome = GameOutcome.PENDING_ACTION,
            pendingMessage = "Auction started for ${command.propertyId}",
        )
    }

    private fun handlePlaceBid(
        session: GameSession,
        command: GameCommand.PlaceAuctionBid,
    ): GameResult {
        val auction = session.auction
            ?: return reject(session, GameError.AuctionError("No auction in progress"))
        val player = session.players[command.playerId]
            ?: return reject(session, GameError.NotFound("Player", command.playerId))
        if (player.jailStatus) {
            return reject(session, GameError.AuctionError("Jailed players cannot bid"))
        }
        val expectedBid = auction.currentBid + banking.auctionBidIncrement
        if (command.bidAmount != expectedBid) {
            return reject(
                session,
                GameError.AuctionError(
                    "Bid must be ${com.boardbanker.core.money.MoneyFormatter.format(expectedBid, banking)} " +
                        "(${com.boardbanker.core.money.MoneyFormatter.format(banking.auctionBidIncrement, banking)} increments)",
                ),
            )
        }
        if (player.balance < command.bidAmount) {
            return reject(session, GameError.InsufficientFunds(command.playerId, command.bidAmount, player.balance))
        }
        val updated = session.copy(
            auction = auction.copy(
                currentBid = command.bidAmount,
                currentBidderId = command.playerId,
            ),
        )
        return GameResult(updated)
    }

    private fun handleCompleteAuction(session: GameSession): GameResult {
        val auction = session.auction
            ?: return reject(session, GameError.AuctionError("No auction in progress"))
        val winnerId = auction.currentBidderId
        if (winnerId == null) {
            return reject(session, GameError.AuctionError("No bids placed"))
        }
        val propertyId = auction.propertyId
        val bid = auction.currentBid
        val winner = session.players[winnerId]!!
        if (winner.balance < bid) {
            val debtResult = debtRules.enterDebtResolution(
                session = session.copy(auction = null),
                debtorId = winnerId,
                creditorId = EntityRef.BANK,
                amount = bid,
                reason = DebtReason.PURCHASE,
                propertyId = propertyId,
            )
            if (!debtResult.isSuccess) {
                return reject(session, GameError.Validation(debtResult.error!!))
            }
            return GameResult(
                session = debtResult.session!!,
                outcome = GameOutcome.DEBT_RESOLUTION_REQUIRED,
                transactions = debtResult.transactions,
            )
        }
        val updatedWinner = winner.copy(balance = winner.balance - bid)
        var updated = session.copy(
            players = session.players + (winnerId to updatedWinner),
            properties = session.properties + (
                propertyId to session.properties[propertyId]!!.copy(
                    ownerPlayerId = winnerId,
                    currentRentLevel = 1,
                )
            ),
            auction = null,
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updated,
            type = TransactionType.AUCTION_WIN,
            fromEntity = winnerId,
            toEntity = EntityRef.BANK,
            playerId = winnerId,
            propertyId = propertyId,
            amount = bid,
        )
        updated = sessionAfter
        val bonus = colorSetRules.applyCompletionBonusIfNeeded(updated, propertyId, winnerId)
        updated = bonus.session
        return GameResult(updated, transactions = listOf(tx) + bonus.transactions)
    }

    private fun handleCancelAuction(session: GameSession): GameResult {
        if (session.auction == null) {
            return reject(session, GameError.AuctionError("No auction in progress"))
        }
        return GameResult(session.copy(auction = null))
    }

    private fun handleResolveDebt(
        session: GameSession,
        command: GameCommand.ResolveDebt,
    ): GameResult = handleResolveDebtWithProperties(
        session,
        GameCommand.ResolveDebtWithProperties(listOf(command.propertyId)),
    )

    private fun handleResolveDebtWithProperties(
        session: GameSession,
        command: GameCommand.ResolveDebtWithProperties,
    ): GameResult {
        val result = debtRules.resolveWithProperties(session, command.propertyIds)
        if (!result.isSuccess) {
            return reject(session, GameError.DebtError(result.error!!))
        }
        val outcome = if (result.session!!.status == GameStatus.FINISHED) {
            GameOutcome.BANKRUPTCY
        } else if (result.session.debtResolution != null) {
            GameOutcome.DEBT_RESOLUTION_REQUIRED
        } else {
            GameOutcome.SUCCESS
        }
        return GameResult(result.session, outcome = outcome, transactions = result.transactions)
    }

    private fun handleCheckBankruptcy(session: GameSession): GameResult {
        val result = debtRules.checkBankruptcyIfCannotResolve(session)
        if (!result.isSuccess) {
            return reject(session, GameError.DebtError(result.error!!))
        }
        val outcome = if (result.session!!.status == GameStatus.FINISHED) {
            GameOutcome.BANKRUPTCY
        } else {
            GameOutcome.SUCCESS
        }
        return GameResult(result.session, outcome = outcome, transactions = result.transactions)
    }

    private fun handleUndo(session: GameSession): GameResult {
        if (!undoSupport.canUndo(session)) {
            return reject(session, GameError.UndoNotAllowed("Undo not allowed"))
        }
        val result = undoSupport.undo(session)
        if (!result.isSuccess) {
            return reject(session, GameError.UndoNotAllowed(result.error!!))
        }
        return GameResult(result.session!!, transactions = result.transactions)
    }

    private fun reject(session: GameSession, error: GameError): GameResult =
        GameResult(session, outcome = GameOutcome.REJECTED, error = error)
}
