package com.boardbanker.core.event

import com.boardbanker.core.engine.PhysicalAction
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PendingDiceGamble
import com.boardbanker.core.model.PendingEnergyGridLanding
import com.boardbanker.core.model.PendingEventDraw
import com.boardbanker.core.rules.BoardTraversal
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.rules.DebtRules
import com.boardbanker.core.rules.GoRules
import com.boardbanker.core.rules.JailRules
import com.boardbanker.core.rules.RentLevelOperations
import com.boardbanker.core.transaction.TransactionFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class IndiaEventHandlers(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val debtRules: DebtRules,
    private val jailRules: JailRules,
    private val goRules: GoRules,
) {
    private val rules = definitions.rules

    fun dispatch(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        propertyId: String?,
        targetPlayerId: String?,
        secondPropertyId: String?,
        secondPlayerId: String?,
        fromBoardPosition: Int? = null,
        timestamp: Long,
    ): EventEngine.EventResult = when (rule.actionType) {
        "MOVE_TO_SPACE" -> handleMoveToSpace(session, eventId, actingPlayerId, rule, timestamp)
        "MOVE_BACKWARD" -> handleMoveBackward(session, eventId, actingPlayerId, rule, timestamp)
        "BANK_CREDIT" -> handleBankCredit(session, eventId, actingPlayerId, rule.amount ?: 0, timestamp)
        "BANK_DEBIT" -> handleBankDebit(session, eventId, actingPlayerId, rule.amount ?: 0, timestamp)
        "PAY_EACH_PLAYER" -> handlePayEachPlayer(session, eventId, actingPlayerId, rule, timestamp)
        "COLLECT_FROM_EACH_PLAYER" -> handleCollectFromEachPlayer(session, eventId, actingPlayerId, rule, timestamp)
        "DEBIT_PER_OWNED_PROPERTY" -> handlePerOwnedProperty(
            session, eventId, actingPlayerId, rule.intParam("amountPerProperty") ?: rule.amount ?: 0, debit = true, timestamp,
        )
        "CREDIT_PER_OWNED_PROPERTY" -> handlePerOwnedProperty(
            session, eventId, actingPlayerId, rule.intParam("amountPerProperty") ?: rule.amount ?: 0, debit = false, timestamp,
        )
        "NEXT_RENT_WAIVER" -> handleNextRentWaiver(session, eventId, actingPlayerId, timestamp)
        "GET_OUT_OF_JAIL_PASS" -> handleJailPass(session, eventId, actingPlayerId, rule, timestamp)
        "MOVE_TO_JAIL" -> handleMoveToJail(session, eventId, actingPlayerId, rule, timestamp)
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL" -> handleSelectedPropertyRentChange(
            session, eventId, actingPlayerId, propertyId, +1, rule, timestamp,
        )
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL" -> handleSelectedPropertyRentChange(
            session, eventId, actingPlayerId, propertyId, -1, rule, timestamp,
        )
        "DRAW_ANOTHER_EVENT" -> handleDrawAnotherEvent(session, eventId, actingPlayerId, rule, timestamp)
        "COOPERATIVE_PROPERTY_UPGRADE" -> handleCooperativeUpgrade(
            session, eventId, actingPlayerId, propertyId, secondPropertyId, secondPlayerId ?: targetPlayerId, rule, timestamp,
        )
        "GAMBLE_ON_DICE_ROLL" -> handleGambleStart(session, eventId, actingPlayerId, rule, timestamp)
        "SKIP_NEXT_TURN" -> handleSkipNextTurn(session, eventId, actingPlayerId, timestamp)
        "FORCED_PROPERTY_SELLBACK" -> handleForcedSellback(
            session, eventId, actingPlayerId, propertyId, rule, timestamp,
        )
        "TOP_UP_BALANCE_TO_THRESHOLD" -> handleTopUpBalance(session, eventId, actingPlayerId, rule, timestamp)
        "MOVE_TO_NEAREST_STATION" -> handleMoveToNearestStation(
            session, eventId, actingPlayerId, rule, fromBoardPosition, timestamp,
        )
        "EXTRA_TURN" -> handleExtraTurn(session, eventId, actingPlayerId, rule, timestamp)
        "COMPLETE_COLOR_SET_BONUS_CREDIT" -> handleColorSetBonusCredit(session, eventId, actingPlayerId, rule, timestamp)
        else -> EventEngine.EventResult.failure(
            "Edition '${definitions.editionId}' event '$eventId': unsupported action '${rule.actionType}'",
        )
    }

    fun rollDiceGamble(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        diceResults: List<Int>,
        timestamp: Long,
    ): EventEngine.EventResult {
        val pending = session.pendingDiceGamble
            ?: return EventEngine.EventResult.failure("No dice gamble in progress")
        if (pending.eventId != eventId || pending.actingPlayerId != actingPlayerId) {
            return EventEngine.EventResult.failure("Dice gamble state mismatch")
        }
        if (pending.completed) {
            return EventEngine.EventResult.failure("Dice gamble already completed")
        }
        if (diceResults.size != pending.diceCount) {
            return EventEngine.EventResult.failure("Expected ${pending.diceCount} dice values")
        }
        if (diceResults.any { it !in 1..6 }) {
            return EventEngine.EventResult.failure("Dice values must be between 1 and 6")
        }
        val attemptsUsed = pending.attemptsUsed + 1
        val doubles = diceResults.size >= 2 && diceResults.distinct().size == 1
        if (doubles) {
            return completeGambleSuccess(session, pending, diceResults, timestamp)
        }
        if (attemptsUsed >= pending.maximumAttempts) {
            return completeGambleFailure(session, pending, diceResults, timestamp)
        }
        val updated = session.copy(
            pendingDiceGamble = pending.copy(
                attemptsUsed = attemptsUsed,
                lastRollResults = diceResults,
            ),
        )
        return EventEngine.EventResult.success(
            updated,
            emptyList(),
            pendingMessage = "Roll again (${attemptsUsed}/${pending.maximumAttempts})",
        )
    }

    private fun handleMoveToSpace(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val collectGo = rule.booleanParam("collectGoAmount") ?: false
        var updatedSession = session
        val transactions = mutableListOf<Transaction>()
        if (collectGo) {
            val goSalary = definitions.bankingValues.goSalary
            val creditResult = handleBankCredit(updatedSession, eventId, actingPlayerId, goSalary, timestamp)
            if (!creditResult.isSuccess) return creditResult
            updatedSession = creditResult.session!!
            transactions += creditResult.transactions
        }
        val physical = PhysicalAction(
            instruction = "Move directly to GO.",
            affectedPlayerIds = listOf(actingPlayerId),
            targetSpace = "GO",
        )
        return EventEngine.EventResult.success(updatedSession, transactions, listOf(physical))
    }

    private fun handleMoveBackward(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val spaces = rule.intParam("spaceCount") ?: 3
        val physical = PhysicalAction(
            instruction = "Move backward $spaces spaces, then scan the landed card. Do not collect GO salary.",
            affectedPlayerIds = listOf(actingPlayerId),
        )
        return EventEngine.EventResult.success(session, emptyList(), listOf(physical))
    }

    private fun handleBankCredit(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        amount: Int,
        timestamp: Long,
    ): EventEngine.EventResult = transferFromBank(session, eventId, actingPlayerId, amount, credit = true, timestamp)

    private fun handleBankDebit(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        amount: Int,
        timestamp: Long,
    ): EventEngine.EventResult = transferFromBank(session, eventId, actingPlayerId, amount, credit = false, timestamp)

    private fun transferFromBank(
        session: GameSession,
        eventId: String,
        playerId: String,
        amount: Int,
        credit: Boolean,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (amount <= 0) return EventEngine.EventResult.success(session, emptyList())
        val player = session.players[playerId] ?: return EventEngine.EventResult.failure("Unknown player")
        if (!credit && player.balance < amount) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = playerId,
                creditorId = EntityRef.BANK,
                amount = amount,
                timestamp = timestamp,
            )
            if (!debtResult.isSuccess) return EventEngine.EventResult.failure(debtResult.error ?: "Debt failed")
            return EventEngine.EventResult.success(debtResult.session!!, debtResult.transactions, needsDebtResolution = true)
        }
        val updatedPlayer = player.copy(balance = if (credit) player.balance + amount else player.balance - amount)
        var updatedSession = session.copy(players = session.players + (playerId to updatedPlayer))
        val txType = if (credit) TransactionType.BANK_CREDIT else TransactionType.BANK_DEBIT
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = txType,
            timestamp = timestamp,
            fromEntity = if (credit) EntityRef.BANK else playerId,
            toEntity = if (credit) playerId else EntityRef.BANK,
            playerId = playerId,
            eventId = eventId,
            amount = amount,
            reversible = true,
        )
        return EventEngine.EventResult.success(sessionAfter.copy(undoSnapshot = session.snapshot()), listOf(tx))
    }

    private fun handlePayEachPlayer(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val amount = rule.intParam("amountPerOtherPlayer") ?: rule.amount ?: 0
        return transferBetweenPlayers(session, eventId, actingPlayerId, amount, payerIsActing = true, timestamp)
    }

    private fun handleCollectFromEachPlayer(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val amount = rule.intParam("amountPerOtherPlayer") ?: rule.amount ?: 0
        return transferBetweenPlayers(session, eventId, actingPlayerId, amount, payerIsActing = false, timestamp)
    }

    private fun transferBetweenPlayers(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        amount: Int,
        payerIsActing: Boolean,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (amount <= 0) return EventEngine.EventResult.success(session, emptyList())
        var updatedSession = session
        val transactions = mutableListOf<Transaction>()
        val recipients = otherActivePlayers(session, actingPlayerId)
        for (otherId in recipients) {
            val payerId = if (payerIsActing) actingPlayerId else otherId
            val receiverId = if (payerIsActing) otherId else actingPlayerId
            val payer = updatedSession.players[payerId]!!
            if (payer.balance < amount) {
                val debtResult = debtRules.enterDebtResolution(
                    session = updatedSession,
                    debtorId = payerId,
                    creditorId = receiverId,
                    amount = amount,
                    timestamp = timestamp,
                )
                if (!debtResult.isSuccess) {
                    return EventEngine.EventResult.failure(debtResult.error ?: "Debt failed for $payerId")
                }
                updatedSession = debtResult.session!!
                transactions += debtResult.transactions
                continue
            }
            val receiver = updatedSession.players[receiverId]!!
            updatedSession = updatedSession.copy(
                players = updatedSession.players +
                    (payerId to payer.copy(balance = payer.balance - amount)) +
                    (receiverId to receiver.copy(balance = receiver.balance + amount)),
            )
            val (tx, sessionAfter) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.RENT_PAYMENT,
                timestamp = timestamp,
                fromEntity = payerId,
                toEntity = receiverId,
                playerId = payerId,
                eventId = eventId,
                amount = amount,
                reversible = true,
            )
            updatedSession = sessionAfter
            transactions += tx
        }
        return EventEngine.EventResult.success(updatedSession.copy(undoSnapshot = session.snapshot()), transactions)
    }

    private fun handlePerOwnedProperty(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        amountPerProperty: Int,
        debit: Boolean,
        timestamp: Long,
    ): EventEngine.EventResult {
        val ownedCount = session.properties.values.count { it.ownerPlayerId == actingPlayerId }
        val total = ownedCount * amountPerProperty
        return if (debit) {
            handleBankDebit(session, eventId, actingPlayerId, total, timestamp)
        } else {
            handleBankCredit(session, eventId, actingPlayerId, total, timestamp)
        }
    }

    private fun handleNextRentWaiver(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        timestamp: Long,
    ): EventEngine.EventResult {
        val player = session.players[actingPlayerId]!!
        if (player.pendingRentWaiver) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val updated = player.copy(pendingRentWaiver = true)
        return EventEngine.EventResult.success(
            session.copy(
                players = session.players + (actingPlayerId to updated),
                undoSnapshot = session.snapshot(),
            ),
            emptyList(),
        )
    }

    private fun handleJailPass(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val player = session.players[actingPlayerId]!!
        if (player.jailPassCount > 0) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val updated = player.copy(jailPassCount = rule.intParam("passCount") ?: 1)
        return EventEngine.EventResult.success(
            session.copy(
                players = session.players + (actingPlayerId to updated),
                undoSnapshot = session.snapshot(),
            ),
            emptyList(),
        )
    }

    private fun handleMoveToJail(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val jailResult = jailRules.sendToJail(session, actingPlayerId, timestamp)
        if (!jailResult.isSuccess) return EventEngine.EventResult.failure(jailResult.error ?: "Jail failed")
        val updatedSession = jailResult.session!!
        val physical = PhysicalAction(
            instruction = "Move directly to Jail without collecting GO salary. End your current turn.",
            affectedPlayerIds = listOf(actingPlayerId),
            targetSpace = "JAIL",
        )
        return EventEngine.EventResult.success(updatedSession, jailResult.transactions, listOf(physical))
    }

    private fun handleSelectedPropertyRentChange(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        delta: Int,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (propertyId == null) return EventEngine.EventResult.failure("Property scan required")
        val propertyState = session.properties[propertyId] ?: return EventEngine.EventResult.failure("Unknown property")
        if (propertyState.ownerPlayerId != actingPlayerId) {
            return EventEngine.EventResult.failure("Property must be owned by acting player")
        }
        val oldLevel = propertyState.currentRentLevel
        val newLevel = if (delta > 0) {
            RentLevelOperations.increaseLevel(oldLevel, 1, rules.maximumRentLevel)
        } else {
            RentLevelOperations.decreaseLevel(oldLevel, 1, rules.minimumRentLevel)
        }
        if (newLevel == oldLevel) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val updatedProperties = session.properties + (propertyId to propertyState.copy(currentRentLevel = newLevel))
        var updatedSession = session.copy(properties = updatedProperties, undoSnapshot = session.snapshot())
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            timestamp = timestamp,
            playerId = actingPlayerId,
            propertyId = propertyId,
            eventId = eventId,
            amount = newLevel,
            stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
            stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
        )
        return EventEngine.EventResult.success(sessionAfter, listOf(tx))
    }

    private fun handleDrawAnotherEvent(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (session.pendingEventDraw != null) {
            return EventEngine.EventResult.failure("Event draw already pending")
        }
        val maxDepth = rule.intParam("maximumChainDepth") ?: 3
        val nextDepth = session.eventChainDepth + 1
        if (nextDepth > maxDepth) {
            return EventEngine.EventResult.failure("Maximum chained event depth reached")
        }
        val pending = PendingEventDraw(
            parentEventId = eventId,
            actingPlayerId = actingPlayerId,
            chainDepth = nextDepth,
            maximumChainDepth = maxDepth,
        )
        val physical = PhysicalAction(
            instruction = "Scan the additional Event Card and resolve it completely.",
            affectedPlayerIds = listOf(actingPlayerId),
        )
        return EventEngine.EventResult.success(
            session.copy(
                pendingEventDraw = pending,
                eventChainDepth = nextDepth,
                undoSnapshot = session.snapshot(),
            ),
            emptyList(),
            listOf(physical),
            pendingMessage = "Scan the additional Event Card",
        )
    }

    private fun handleCooperativeUpgrade(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        secondPropertyId: String?,
        otherPlayerId: String?,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (propertyId == null || secondPropertyId == null || otherPlayerId == null) {
            return EventEngine.EventResult.failure("Two players and two properties required")
        }
        if (propertyId == secondPropertyId) {
            return EventEngine.EventResult.failure("Select two different properties")
        }
        val first = session.properties[propertyId] ?: return EventEngine.EventResult.failure("Unknown property")
        val second = session.properties[secondPropertyId] ?: return EventEngine.EventResult.failure("Unknown second property")
        if (first.ownerPlayerId != actingPlayerId) {
            return EventEngine.EventResult.failure("First property must be owned by acting player")
        }
        if (second.ownerPlayerId != otherPlayerId) {
            return EventEngine.EventResult.failure("Second property must be owned by the selected player")
        }
        val changes = mutableMapOf<String, Int>()
        for ((id, state) in listOf(propertyId to first, secondPropertyId to second)) {
            val newLevel = RentLevelOperations.increaseLevel(state.currentRentLevel, 1, rules.maximumRentLevel)
            if (newLevel == state.currentRentLevel) {
                return EventEngine.EventResult.success(session, emptyList())
            }
            changes[id] = newLevel
        }
        var updatedSession = session.copy(
            properties = RentLevelOperations.applyRentLevelChanges(session.properties, changes),
            undoSnapshot = session.snapshot(),
        )
        val transactions = changes.map { (id, newLevel) ->
            val oldLevel = session.properties[id]!!.currentRentLevel
            val (tx, sessionAfter) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
                timestamp = timestamp,
                playerId = session.properties[id]!!.ownerPlayerId,
                propertyId = id,
                eventId = eventId,
                amount = newLevel,
                stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
                stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
            )
            updatedSession = sessionAfter
            tx
        }
        return EventEngine.EventResult.success(updatedSession, transactions)
    }

    private fun handleGambleStart(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        if (session.pendingDiceGamble != null) {
            return EventEngine.EventResult.failure("Dice gamble already in progress")
        }
        val pending = PendingDiceGamble(
            eventId = eventId,
            actingPlayerId = actingPlayerId,
            attemptsUsed = 0,
            maximumAttempts = rule.intParam("maximumAttempts") ?: 3,
            jackpotAmount = rule.intParam("jackpotAmount") ?: 0,
            penaltyAmount = rule.intParam("penaltyAmount") ?: 0,
            diceCount = rule.intParam("diceCount") ?: 2,
        )
        return EventEngine.EventResult.success(
            session.copy(pendingDiceGamble = pending, undoSnapshot = session.snapshot()),
            emptyList(),
            pendingMessage = "Roll Dice",
        )
    }

    private fun completeGambleSuccess(
        session: GameSession,
        pending: PendingDiceGamble,
        diceResults: List<Int>,
        timestamp: Long,
    ): EventEngine.EventResult {
        val cleared = session.copy(pendingDiceGamble = pending.copy(lastRollResults = diceResults, completed = true))
        val credit = handleBankCredit(cleared, pending.eventId, pending.actingPlayerId, pending.jackpotAmount, timestamp)
        return credit.copy(
            session = credit.session?.copy(pendingDiceGamble = null),
        )
    }

    private fun completeGambleFailure(
        session: GameSession,
        pending: PendingDiceGamble,
        diceResults: List<Int>,
        timestamp: Long,
    ): EventEngine.EventResult {
        val cleared = session.copy(pendingDiceGamble = pending.copy(lastRollResults = diceResults, completed = true))
        val debit = handleBankDebit(cleared, pending.eventId, pending.actingPlayerId, pending.penaltyAmount, timestamp)
        return debit.copy(
            session = debit.session?.copy(pendingDiceGamble = null),
        )
    }

    private fun handleSkipNextTurn(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        timestamp: Long,
    ): EventEngine.EventResult {
        val player = session.players[actingPlayerId]!!
        if (player.pendingSkipTurnCount > 0) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val updated = player.copy(pendingSkipTurnCount = 1)
        return EventEngine.EventResult.success(
            session.copy(
                players = session.players + (actingPlayerId to updated),
                undoSnapshot = session.snapshot(),
            ),
            emptyList(),
        )
    }

    private fun handleForcedSellback(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val owned = session.properties.filter { it.value.ownerPlayerId == actingPlayerId }
        if (owned.isEmpty()) return EventEngine.EventResult.success(session, emptyList())
        val minPrice = owned.minOf { definitions.properties[it.key]!!.purchasePrice }
        val candidates = owned.filter { definitions.properties[it.key]!!.purchasePrice == minPrice }.keys.toList()
        val selectedId = when {
            candidates.size == 1 -> candidates.first()
            propertyId != null && propertyId in candidates -> propertyId
            propertyId != null && propertyId in owned -> propertyId
            else -> return EventEngine.EventResult.failure("Select one of your lowest-value properties")
        }
        val propertyDef = definitions.properties[selectedId]!!
        val multiplier = rule.doubleParam("payoutMultiplier") ?: 2.0
        val payout = (propertyDef.purchasePrice * multiplier).toInt()
        val propertyState = session.properties[selectedId]!!
        val initialLevel = propertyDef.initialRentLevel
        var updatedSession = session.copy(
            properties = session.properties + (
                selectedId to PropertyState(
                    propertyId = selectedId,
                    ownerPlayerId = null,
                    currentRentLevel = initialLevel,
                )
            ),
            temporaryEffects = session.temporaryEffects.filterNot {
                it.targetScope == selectedId
            },
            undoSnapshot = session.snapshot(),
        )
        val credit = handleBankCredit(updatedSession, eventId, actingPlayerId, payout, timestamp)
        return credit
    }

    private fun handleTopUpBalance(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val threshold = rule.intParam("thresholdAmount") ?: rule.amount ?: 0
        val player = session.players[actingPlayerId]!!
        if (player.balance >= threshold) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val topUp = threshold - player.balance
        return handleBankCredit(session, eventId, actingPlayerId, topUp, timestamp)
    }

    private fun handleMoveToNearestStation(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        fromBoardPosition: Int?,
        timestamp: Long,
    ): EventEngine.EventResult {
        val rawSemantic = rule.parameters["stationSemanticType"]?.jsonPrimitive?.content
        val semanticType = when (rawSemantic) {
            "ENERGY_STATION", "ENERGY_GRID", null -> "ENERGY_GRID"
            else -> rawSemantic
        }
        if (semanticType != "ENERGY_GRID") {
            return EventEngine.EventResult.failure("Unsupported station semantic type: $semanticType")
        }
        if (definitions.energyGrids.isEmpty()) {
            val physical = PhysicalAction(
                instruction = "Move forward to the next Energy Grid, then scan the landed card.",
                affectedPlayerIds = listOf(actingPlayerId),
            )
            return EventEngine.EventResult.success(session, emptyList(), listOf(physical))
        }
        if (fromBoardPosition == null) {
            val physical = PhysicalAction(
                instruction = "Move forward to the next Energy Grid, then scan the landed card.",
                affectedPlayerIds = listOf(actingPlayerId),
            )
            return EventEngine.EventResult.success(session, emptyList(), listOf(physical))
        }
        val layout = definitions.boardLayout
        if (fromBoardPosition !in 0 until layout.size) {
            return EventEngine.EventResult.failure("Invalid board position: $fromBoardPosition")
        }
        val targetSpace = BoardTraversal.nextEnergyGridSpace(definitions, fromBoardPosition)
            ?: return EventEngine.EventResult.failure("No energy grid space found on board")
        val targetGridId = targetSpace.targetId
            ?: return EventEngine.EventResult.failure("Energy grid space is missing targetId")
        if (!definitions.energyGrids.containsKey(targetGridId)) {
            return EventEngine.EventResult.failure("Unknown energy grid on board: $targetGridId")
        }

        var updatedSession = session
        val transactions = mutableListOf<Transaction>()
        val collectGo = rule.booleanParam("collectGoIfPassed") ?: true
        if (collectGo && BoardTraversal.passedGoOnForwardMove(fromBoardPosition, targetSpace.position)) {
            val goSalary = definitions.bankingValues.goSalary
            val creditResult = handleBankCredit(updatedSession, eventId, actingPlayerId, goSalary, timestamp)
            if (!creditResult.isSuccess) return creditResult
            updatedSession = creditResult.session!!
            transactions += creditResult.transactions
        }

        val grid = definitions.energyGrids[targetGridId]!!
        val boardNumber = layout.boardNumberForEnergyGrid(targetGridId)
        val displayName = if (boardNumber != null) "[$boardNumber] ${grid.name}" else grid.name
        updatedSession = updatedSession.copy(
            undoSnapshot = session.snapshot(),
            pendingEnergyGridLanding = PendingEnergyGridLanding(
                actingPlayerId = actingPlayerId,
                energyGridId = targetGridId,
                sourceEventId = eventId,
            ),
        )
        val physical = PhysicalAction(
            instruction = "Move forward to $displayName, then scan the Energy Grid card.",
            affectedPlayerIds = listOf(actingPlayerId),
            targetSpace = targetSpace.spaceId,
        )
        return EventEngine.EventResult.success(updatedSession, transactions, listOf(physical))
    }

    private fun handleExtraTurn(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val player = session.players[actingPlayerId]!!
        val stackBehaviour = rule.parameters["stackBehaviour"]?.jsonPrimitive?.content ?: "DO_NOT_STACK"
        if (stackBehaviour == "DO_NOT_STACK") {
            if (player.pendingExtraTurn) {
                return EventEngine.EventResult.success(session, emptyList())
            }
            val turnState = session.turnState
            if (turnState?.activePlayerId == actingPlayerId && turnState.turnKind == TurnKind.EXTRA) {
                return EventEngine.EventResult.success(session, emptyList())
            }
        }
        val updated = player.copy(pendingExtraTurn = true)
        var updatedSession = session.copy(
            players = session.players + (actingPlayerId to updated),
            undoSnapshot = session.snapshot(),
        )
        val (grantTx, sessionAfterGrant) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EXTRA_TURN_GRANTED,
            timestamp = timestamp,
            playerId = actingPlayerId,
            eventId = eventId,
            reversible = true,
        )
        return EventEngine.EventResult.success(sessionAfterGrant, listOf(grantTx))
    }

    private fun handleColorSetBonusCredit(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventEngine.EventResult {
        val completeGroups = definitions.boardRelationships.colorGroups.filter { (_, propertyIds) ->
            propertyIds.isNotEmpty() && propertyIds.all { id ->
                session.properties[id]?.ownerPlayerId == actingPlayerId
            }
        }
        if (completeGroups.isEmpty()) {
            return EventEngine.EventResult.success(session, emptyList())
        }
        val base = rule.intParam("baseRebateAmount") ?: rule.amount ?: 0
        val multiplier = rule.doubleParam("rewardMultiplier") ?: 1.0
        val maxCredit = rule.intParam("maximumCreditAmount")
        var credit = (base * multiplier).toInt()
        if (maxCredit != null) {
            credit = minOf(credit, maxCredit)
        }
        return handleBankCredit(session, eventId, actingPlayerId, credit, timestamp)
    }

    private fun otherActivePlayers(session: GameSession, actingPlayerId: String): List<String> =
        session.players.values
            .filter { it.active && !it.bankrupt && it.playerId != actingPlayerId }
            .map { it.playerId }
            .sorted()

    private fun EventActionDefinition.intParam(key: String): Int? =
        parameters[key]?.jsonPrimitive?.intOrNull

    private fun EventActionDefinition.booleanParam(key: String): Boolean? =
        parameters[key]?.jsonPrimitive?.booleanOrNull

    private fun EventActionDefinition.doubleParam(key: String): Double? =
        parameters[key]?.jsonPrimitive?.doubleOrNull
}
