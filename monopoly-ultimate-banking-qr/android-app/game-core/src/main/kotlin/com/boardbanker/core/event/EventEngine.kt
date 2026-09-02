package com.boardbanker.core.event

import com.boardbanker.core.engine.PhysicalAction
import com.boardbanker.core.model.ColorGroupState
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.PendingEventChoice
import com.boardbanker.core.model.PendingEventExecution
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.TemporaryEffect
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.DebtRules
import com.boardbanker.core.rules.GoRules
import com.boardbanker.core.rules.JailRules
import com.boardbanker.core.rules.RentLevelOperations
import com.boardbanker.core.transaction.TransactionFactory
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class EventEngine(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
    private val jailRules: JailRules,
    private val debtRules: DebtRules,
) {
    private val rules = definitions.rules
    private val policies = definitions.policies
    private val indiaHandlers = IndiaEventHandlers(
        definitions = definitions,
        transactionFactory = transactionFactory,
        debtRules = debtRules,
        jailRules = jailRules,
        goRules = GoRules(definitions, transactionFactory),
    )

    companion object {
        private val INDIA_ACTION_TYPES = setOf(
            "MOVE_TO_SPACE",
            "MOVE_BACKWARD",
            "BANK_CREDIT",
            "BANK_DEBIT",
            "PAY_EACH_PLAYER",
            "COLLECT_FROM_EACH_PLAYER",
            "DEBIT_PER_OWNED_PROPERTY",
            "CREDIT_PER_OWNED_PROPERTY",
            "NEXT_RENT_WAIVER",
            "GET_OUT_OF_JAIL_PASS",
            "MOVE_TO_JAIL",
            "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
            "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
            "DRAW_ANOTHER_EVENT",
            "COOPERATIVE_PROPERTY_UPGRADE",
            "GAMBLE_ON_DICE_ROLL",
            "SKIP_NEXT_TURN",
            "FORCED_PROPERTY_SELLBACK",
            "TOP_UP_BALANCE_TO_THRESHOLD",
            "MOVE_TO_NEAREST_STATION",
            "EXTRA_TURN",
            "COMPLETE_COLOR_SET_BONUS_CREDIT",
        )
    }

    fun rollEventDice(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        diceResults: List<Int>,
        timestamp: Long = System.currentTimeMillis(),
    ): EventResult = indiaHandlers.rollDiceGamble(session, eventId, actingPlayerId, diceResults, timestamp)

    fun apply(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String? = null,
        targetPlayerId: String? = null,
        secondPropertyId: String? = null,
        secondPlayerId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): EventResult {
        val event = definitions.events[eventId]
            ?: return EventResult.failure("Unknown event $eventId")

        val existingPending = session.pendingEventExecution
        if (existingPending != null) {
            if (existingPending.eventId != eventId) {
                return EventResult.failure("Another event is in progress")
            }
            if (existingPending.actingPlayerId != actingPlayerId) {
                return EventResult.failure("Acting player mismatch for pending event")
            }
        }

        val resolvedPropertyId = propertyId ?: existingPending?.propertyId
        val resolvedTargetPlayerId = targetPlayerId ?: existingPending?.targetPlayerId
        val resolvedSecondPropertyId = secondPropertyId ?: existingPending?.secondPropertyId
        val resolvedSecondPlayerId = secondPlayerId ?: existingPending?.secondPlayerId

        var currentSession = session.copy(pendingEventExecution = null)
        val accumulatedTransactions = mutableListOf<Transaction>()
        val accumulatedPhysical = mutableListOf<PhysicalAction>()
        var pendingMessage: String? = null
        var needsDebtResolution = false

        var actionIndex = existingPending?.currentActionIndex ?: 0
        while (actionIndex < event.actions.size) {
            val rule = event.actions[actionIndex]
            missingInputMessage(
                rule = rule,
                propertyId = resolvedPropertyId,
                targetPlayerId = resolvedTargetPlayerId,
                secondPropertyId = resolvedSecondPropertyId,
                secondPlayerId = resolvedSecondPlayerId,
            )?.let { message ->
                return EventResult.success(
                    session = currentSession.copy(
                        pendingEventExecution = PendingEventExecution(
                            eventId = eventId,
                            actingPlayerId = actingPlayerId,
                            currentActionIndex = actionIndex,
                            propertyId = resolvedPropertyId,
                            targetPlayerId = resolvedTargetPlayerId,
                            secondPropertyId = resolvedSecondPropertyId,
                            secondPlayerId = resolvedSecondPlayerId,
                        ),
                    ),
                    transactions = accumulatedTransactions,
                    physicalActions = accumulatedPhysical,
                    pendingMessage = message,
                    needsDebtResolution = needsDebtResolution,
                )
            }

            val isLastAction = actionIndex == event.actions.lastIndex
            val actionResult = dispatchAction(
                session = currentSession,
                eventId = eventId,
                actingPlayerId = actingPlayerId,
                rule = rule,
                propertyId = resolvedPropertyId,
                targetPlayerId = resolvedTargetPlayerId,
                secondPropertyId = resolvedSecondPropertyId,
                secondPlayerId = resolvedSecondPlayerId,
                timestamp = timestamp,
                finalizeEvent = isLastAction,
            )
            if (!actionResult.isSuccess) {
                return actionResult
            }

            val actionTransactions = if (isLastAction) {
                actionResult.transactions
            } else {
                actionResult.transactions.filter { it.transactionType != TransactionType.EVENT_APPLIED }
            }
            accumulatedTransactions += actionTransactions
            accumulatedPhysical += actionResult.physicalActions
            currentSession = actionResult.session!!
            pendingMessage = actionResult.pendingMessage ?: pendingMessage
            needsDebtResolution = needsDebtResolution || actionResult.needsDebtResolution

            if (currentSession.pendingEventChoice != null) {
                val nextIndex = actionIndex + 1
                val continuation = if (nextIndex < event.actions.size) {
                    PendingEventExecution(
                        eventId = eventId,
                        actingPlayerId = actingPlayerId,
                        currentActionIndex = nextIndex,
                        propertyId = resolvedPropertyId,
                        targetPlayerId = resolvedTargetPlayerId,
                        secondPropertyId = resolvedSecondPropertyId,
                        secondPlayerId = resolvedSecondPlayerId,
                    )
                } else {
                    null
                }
                return EventResult.success(
                    session = currentSession.copy(pendingEventExecution = continuation),
                    transactions = accumulatedTransactions,
                    physicalActions = accumulatedPhysical,
                    pendingMessage = pendingMessage,
                    needsDebtResolution = needsDebtResolution,
                )
            }

            actionIndex++
        }

        return EventResult.success(
            session = currentSession.copy(pendingEventExecution = null),
            transactions = accumulatedTransactions,
            physicalActions = accumulatedPhysical,
            pendingMessage = pendingMessage,
            needsDebtResolution = needsDebtResolution,
        )
    }

    private fun missingInputMessage(
        rule: EventActionDefinition,
        propertyId: String?,
        targetPlayerId: String?,
        secondPropertyId: String?,
        secondPlayerId: String?,
    ): String? = when (rule.actionType) {
        "MOVE_THEN_PROPERTY_CHOICE",
        "INCREASE_COLOR_SET_RENT_LEVEL",
        "DECREASE_COLOR_SET_RENT_LEVEL",
        "RESET_PROPERTY_RENT_LEVEL",
        "SET_PROPERTY_RENT_LEVEL",
        "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS",
        "DECREASE_BOARD_SIDE_RENT_LEVEL",
        "INCREASE_BOARD_SIDE_RENT_LEVEL",
        -> if (propertyId == null) "Property scan required" else null
        "SWAP_PROPERTIES" -> when {
            targetPlayerId == null && secondPlayerId == null -> "Two players and two properties required"
            propertyId == null || secondPropertyId == null -> "Two players and two properties required"
            else -> null
        }
        "CREDIT_BOTH_PLAYERS" -> if (targetPlayerId == null && secondPlayerId == null) {
            "Second player required"
        } else {
            null
        }
        "SEND_PLAYER_TO_JAIL" -> if (targetPlayerId == null) "Target player required" else null
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "FORCED_PROPERTY_SELLBACK",
        -> if (propertyId == null) "Property scan required" else null
        "COOPERATIVE_PROPERTY_UPGRADE" -> when {
            targetPlayerId == null && secondPlayerId == null -> "Scan another player's card"
            propertyId == null -> "Scan one of your eligible Property Cards"
            secondPropertyId == null -> "Scan that player's eligible Property Card"
            else -> null
        }
        else -> null
    }

    private fun dispatchAction(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        rule: EventActionDefinition,
        propertyId: String?,
        targetPlayerId: String?,
        secondPropertyId: String?,
        secondPlayerId: String?,
        timestamp: Long,
        finalizeEvent: Boolean,
    ): EventResult {
        if (rule.actionType in INDIA_ACTION_TYPES) {
            val indiaResult = indiaHandlers.dispatch(
                session = session,
                eventId = eventId,
                actingPlayerId = actingPlayerId,
                rule = rule,
                propertyId = propertyId,
                targetPlayerId = targetPlayerId,
                secondPropertyId = secondPropertyId,
                secondPlayerId = secondPlayerId,
                timestamp = timestamp,
            )
            if (!indiaResult.isSuccess) {
                return indiaResult
            }
            val (sessionAfter, transactions) = appendEventAppliedIfNeeded(
                session = indiaResult.session!!,
                eventId = eventId,
                actingPlayerId = actingPlayerId,
                propertyId = propertyId,
                transactions = if (finalizeEvent) {
                    indiaResult.transactions
                } else {
                    indiaResult.transactions.filter { it.transactionType != TransactionType.EVENT_APPLIED }
                },
                timestamp = timestamp,
                finalizeEvent = finalizeEvent,
            )
            return indiaResult.copy(session = sessionAfter, transactions = transactions)
        }

        val result = when (rule.actionType) {
            "MOVE_THEN_PROPERTY_CHOICE" -> handleMoveThenPropertyChoice(
                session, eventId, actingPlayerId, propertyId, timestamp,
            )
            "INCREASE_COLOR_SET_RENT_LEVEL" -> handleColorSetChange(
                session, eventId, actingPlayerId, propertyId, +1, timestamp,
            )
            "DECREASE_COLOR_SET_RENT_LEVEL" -> handleColorSetChange(
                session, eventId, actingPlayerId, propertyId, -1, timestamp,
            )
            "RESET_PROPERTY_RENT_LEVEL" -> handleResetRentLevel(
                session, eventId, propertyId, timestamp,
            )
            "SET_PROPERTY_RENT_LEVEL" -> handleSetRentLevel(
                session, eventId, actingPlayerId, propertyId, rule.amount ?: 5, timestamp,
            )
            "SWAP_PROPERTIES" -> handleSwapProperties(
                session,
                eventId,
                actingPlayerId,
                targetPlayerId ?: secondPlayerId,
                propertyId,
                secondPropertyId,
                timestamp,
            )
            "PAY_PER_OWNED_PROPERTY" -> handlePayPerOwnedProperty(
                session,
                eventId,
                actingPlayerId,
                definitions.bankingValues.eventAmounts.m50,
                timestamp,
            )
            "CREDIT_BOTH_PLAYERS" -> handleCreditBothPlayers(
                session,
                eventId,
                actingPlayerId,
                targetPlayerId ?: secondPlayerId,
                definitions.bankingValues.eventAmounts.m200,
                timestamp,
            )
            "TEMPORARY_RENT_CAP" -> handleTemporaryRentCap(session, eventId, rule, timestamp)
            "SEND_PLAYER_TO_JAIL" -> handleSendToJail(session, eventId, targetPlayerId, timestamp)
            "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS" -> handleNeighbourAdjust(
                session, eventId, actingPlayerId, propertyId, timestamp,
            )
            "DECREASE_BOARD_SIDE_RENT_LEVEL" -> handleBoardSideChange(
                session, eventId, propertyId, -1, timestamp,
            )
            "INCREASE_BOARD_SIDE_RENT_LEVEL" -> handleBoardSideChange(
                session, eventId, propertyId, +1, timestamp,
            )
            "TOTAL_GRIDLOCK_V1" -> handleTotalGridlock(session, eventId, timestamp)
            else -> EventResult.failure("Unsupported action type ${rule.actionType}")
        }
        if (!result.isSuccess) {
            return result
        }
        val (sessionAfter, transactions) = appendEventAppliedIfNeeded(
            session = result.session!!,
            eventId = eventId,
            actingPlayerId = actingPlayerId,
            propertyId = propertyId,
            transactions = if (finalizeEvent) {
                result.transactions
            } else {
                result.transactions.filter { it.transactionType != TransactionType.EVENT_APPLIED }
            },
            timestamp = timestamp,
            finalizeEvent = finalizeEvent,
        )
        return result.copy(session = sessionAfter, transactions = transactions)
    }

    private fun appendEventAppliedIfNeeded(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        transactions: List<Transaction>,
        timestamp: Long,
        finalizeEvent: Boolean,
    ): Pair<GameSession, List<Transaction>> {
        if (!finalizeEvent || transactions.any { it.transactionType == TransactionType.EVENT_APPLIED }) {
            return session to transactions
        }
        val (tx, sessionAfter) = transactionFactory.create(
            session = session,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        return sessionAfter to (transactions + tx)
    }

    private fun handleMoveThenPropertyChoice(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) {
            return EventResult.failure("Property scan required")
        }
        val physical = PhysicalAction(
            instruction = "Move your token to the selected property.",
            affectedPlayerIds = listOf(actingPlayerId),
            targetSpace = propertyId,
        )
        val pending = PendingEventChoice(eventId, actingPlayerId, propertyId)
        var updatedSession = session.copy(pendingEventChoice = pending)
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        return EventResult.success(
            sessionAfter,
            listOf(tx),
            physicalActions = listOf(physical),
            pendingMessage = "Choose BUY, AUCTION, or RAISE_RENT_LEVEL",
        )
    }

    private fun handleColorSetChange(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        delta: Int,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) return EventResult.failure("Property scan required")
        val propertyDef = definitions.properties[propertyId]
            ?: return EventResult.failure("Unknown property")
        val propertyState = session.properties[propertyId]
            ?: return EventResult.failure("Property state missing")
        if (propertyState.ownerPlayerId != actingPlayerId) {
            return EventResult.failure("Property must be owned by acting player")
        }

        val colorGroup = propertyDef.colorGroup
        val groupIds = definitions.boardRelationships.colorGroups[colorGroup] ?: emptyList()
        val changes = groupIds
            .filter { session.properties[it]?.ownerPlayerId == actingPlayerId }
            .associateWith { id ->
                val current = session.properties[id]!!.currentRentLevel
                if (delta > 0) {
                    RentLevelOperations.increaseLevel(current, delta, rules.maximumRentLevel)
                } else {
                    RentLevelOperations.decreaseLevel(current, -delta, rules.minimumRentLevel)
                }
            }

        if (changes.isEmpty()) {
            return EventResult.success(session, emptyList())
        }

        val oldLevel = propertyState.currentRentLevel
        val newLevel = changes[propertyId] ?: oldLevel
        val updatedProperties = RentLevelOperations.applyRentLevelChanges(session.properties, changes)
        var updatedSession = session.copy(properties = updatedProperties)
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        val (levelTx, finalSession) = transactionFactory.create(
            session = sessionAfter,
            type = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            timestamp = timestamp,
            playerId = actingPlayerId,
            propertyId = propertyId,
            amount = newLevel,
            stateBefore = RentLevelChangeSnapshot.stateBefore(oldLevel),
            stateAfter = RentLevelChangeSnapshot.stateAfter(newLevel),
        )
        return EventResult.success(finalSession, listOf(tx, levelTx))
    }

    private fun handleResetRentLevel(
        session: GameSession,
        eventId: String,
        propertyId: String?,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) return EventResult.failure("Property scan required")
        val propertyState = session.properties[propertyId]
            ?: return EventResult.failure("Property state missing")
        if (propertyState.ownerPlayerId == null) {
            return EventResult.success(session, emptyList())
        }
        val updatedProperty = propertyState.copy(currentRentLevel = 1)
        var updatedSession = session.copy(
            properties = session.properties + (propertyId to updatedProperty),
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            propertyId = propertyId,
        )
        return EventResult.success(sessionAfter, listOf(tx))
    }

    private fun handleSetRentLevel(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        level: Int,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) return EventResult.failure("Property scan required")
        val propertyState = session.properties[propertyId]
            ?: return EventResult.failure("Property state missing")
        if (propertyState.ownerPlayerId != actingPlayerId) {
            return EventResult.failure("Property must be owned by acting player")
        }
        val newLevel = RentLevelOperations.clampLevel(level, rules.minimumRentLevel, rules.maximumRentLevel)
        val updatedProperty = propertyState.copy(currentRentLevel = newLevel)
        var updatedSession = session.copy(
            properties = session.properties + (propertyId to updatedProperty),
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
            amount = newLevel,
        )
        return EventResult.success(sessionAfter, listOf(tx))
    }

    private fun handleSwapProperties(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        targetPlayerId: String?,
        propertyId: String?,
        secondPropertyId: String?,
        timestamp: Long,
    ): EventResult {
        if (targetPlayerId == null || propertyId == null || secondPropertyId == null) {
            return EventResult.failure("Two players and two properties required")
        }
        val prop1 = session.properties[propertyId] ?: return EventResult.failure("Unknown property 1")
        val prop2 = session.properties[secondPropertyId] ?: return EventResult.failure("Unknown property 2")
        if (prop1.ownerPlayerId != actingPlayerId) {
            return EventResult.failure("First property must belong to acting player")
        }
        if (prop2.ownerPlayerId != targetPlayerId) {
            return EventResult.failure("Second property must belong to target player")
        }
        val updated1 = prop1.copy(ownerPlayerId = targetPlayerId)
        val updated2 = prop2.copy(ownerPlayerId = actingPlayerId)
        var updatedSession = session.copy(
            properties = session.properties +
                (propertyId to updated1) +
                (secondPropertyId to updated2),
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        val (swapTx, finalSession) = transactionFactory.create(
            session = sessionAfter,
            type = TransactionType.PROPERTY_SWAP,
            timestamp = timestamp,
            fromEntity = actingPlayerId,
            toEntity = targetPlayerId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        val physical = PhysicalAction(
            instruction = "Exchange physical property cards between players.",
            affectedPlayerIds = listOf(actingPlayerId, targetPlayerId),
        )
        return EventResult.success(finalSession, listOf(tx, swapTx), physicalActions = listOf(physical))
    }

    private fun handlePayPerOwnedProperty(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        perPropertyAmount: Int,
        timestamp: Long,
    ): EventResult {
        val ownedCount = session.properties.values.count { it.ownerPlayerId == actingPlayerId }
        val total = ownedCount * perPropertyAmount
        if (total == 0) {
            return EventResult.success(session, emptyList())
        }
        val player = session.players[actingPlayerId]!!
        if (player.balance < total) {
            val debtResult = debtRules.enterDebtResolution(
                session = session,
                debtorId = actingPlayerId,
                creditorId = EntityRef.BANK,
                amount = total,
                timestamp = timestamp,
            )
            if (!debtResult.isSuccess) return EventResult.failure(debtResult.error ?: "Debt failed")
            val (tx, sessionAfter) = transactionFactory.create(
                session = debtResult.session!!,
                type = TransactionType.EVENT_APPLIED,
                timestamp = timestamp,
                eventId = eventId,
                playerId = actingPlayerId,
                amount = total,
            )
            return EventResult.success(sessionAfter, debtResult.transactions + tx, needsDebtResolution = true)
        }
        val updatedPlayer = player.copy(balance = player.balance - total)
        var updatedSession = session.copy(
            players = session.players + (actingPlayerId to updatedPlayer),
        )
        val (debitTx, sessionAfterDebit) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_DEBIT,
            timestamp = timestamp,
            fromEntity = actingPlayerId,
            toEntity = EntityRef.BANK,
            playerId = actingPlayerId,
            amount = total,
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = sessionAfterDebit,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            amount = total,
        )
        return EventResult.success(sessionAfter, listOf(debitTx, tx))
    }

    private fun handleCreditBothPlayers(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        otherPlayerId: String?,
        amount: Int,
        timestamp: Long,
    ): EventResult {
        if (otherPlayerId == null) return EventResult.failure("Second player required")
        val player1 = session.players[actingPlayerId] ?: return EventResult.failure("Unknown acting player")
        val player2 = session.players[otherPlayerId] ?: return EventResult.failure("Unknown target player")
        var updatedSession = session.copy(
            players = session.players +
                (actingPlayerId to player1.copy(balance = player1.balance + amount)) +
                (otherPlayerId to player2.copy(balance = player2.balance + amount)),
        )
        val transactions = mutableListOf<Transaction>()
        val (credit1Tx, session1) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_CREDIT,
            timestamp = timestamp,
            fromEntity = EntityRef.BANK,
            toEntity = actingPlayerId,
            playerId = actingPlayerId,
            amount = amount,
        )
        transactions += credit1Tx
        updatedSession = session1
        val (credit2Tx, session2) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.BANK_CREDIT,
            timestamp = timestamp,
            fromEntity = EntityRef.BANK,
            toEntity = otherPlayerId,
            playerId = otherPlayerId,
            amount = amount,
        )
        transactions += credit2Tx
        updatedSession = session2
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            amount = amount,
        )
        return EventResult.success(sessionAfter, transactions + tx)
    }

    private fun handleTemporaryRentCap(
        session: GameSession,
        eventId: String,
        rule: EventActionDefinition,
        timestamp: Long,
    ): EventResult {
        val remainingUses = rule.parameters["durationRentPayments"]
            ?.jsonPrimitive?.intOrNull ?: definitions.rules.temporaryEffects.evt13RemainingUses
        val effect = TemporaryEffect(
            effectId = "${eventId}_EFFECT",
            effectType = definitions.rules.temporaryEffects.evt13EffectType,
            remainingUses = remainingUses,
            createdByEventId = eventId,
            targetScope = "GLOBAL",
            active = true,
        )
        var updatedSession = session.copy(
            temporaryEffects = session.temporaryEffects + effect,
        )
        val (effectTx, sessionAfterEffect) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.TEMPORARY_EFFECT_CREATED,
            timestamp = timestamp,
            eventId = eventId,
            amount = remainingUses,
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = sessionAfterEffect,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
        )
        return EventResult.success(sessionAfter, listOf(effectTx, tx))
    }

    private fun handleSendToJail(
        session: GameSession,
        eventId: String,
        targetPlayerId: String?,
        timestamp: Long,
    ): EventResult {
        if (targetPlayerId == null) return EventResult.failure("Target player required")
        val jailResult = jailRules.sendToJail(session, targetPlayerId, timestamp)
        if (!jailResult.isSuccess) return EventResult.failure(jailResult.error ?: "Jail failed")
        val (tx, sessionAfter) = transactionFactory.create(
            session = jailResult.session!!,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = targetPlayerId,
        )
        return EventResult.success(sessionAfter, jailResult.transactions + tx)
    }

    private fun handleNeighbourAdjust(
        session: GameSession,
        eventId: String,
        actingPlayerId: String,
        propertyId: String?,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) return EventResult.failure("Property scan required")
        val propertyState = session.properties[propertyId]
            ?: return EventResult.failure("Property state missing")
        if (propertyState.ownerPlayerId != actingPlayerId) {
            return EventResult.failure("Property must be owned by acting player")
        }
        val neighbours = definitions.boardRelationships.neighbours[propertyId] ?: emptyList()
        val changes = mutableMapOf<String, Int>()
        changes[propertyId] = RentLevelOperations.increaseLevel(
            propertyState.currentRentLevel,
            1,
            rules.maximumRentLevel,
        )
        for (neighbourId in neighbours) {
            val neighbour = session.properties[neighbourId]
            if (neighbour?.ownerPlayerId != null) {
                changes[neighbourId] = RentLevelOperations.decreaseLevel(
                    neighbour.currentRentLevel,
                    1,
                    rules.minimumRentLevel,
                )
            }
        }
        val updatedProperties = RentLevelOperations.applyRentLevelChanges(session.properties, changes)
        var updatedSession = session.copy(properties = updatedProperties)
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            playerId = actingPlayerId,
            propertyId = propertyId,
        )
        return EventResult.success(sessionAfter, listOf(tx))
    }

    private fun handleBoardSideChange(
        session: GameSession,
        eventId: String,
        propertyId: String?,
        delta: Int,
        timestamp: Long,
    ): EventResult {
        if (propertyId == null) return EventResult.failure("Property scan required")
        val side = definitions.boardRelationships.propertyToSide[propertyId]
            ?: return EventResult.failure("Unknown board side for property")
        val sideProperties = definitions.boardRelationships.boardSides[side] ?: emptyList()
        val changes = sideProperties
            .filter { session.properties[it]?.ownerPlayerId != null }
            .associateWith { id ->
                val current = session.properties[id]!!.currentRentLevel
                if (delta > 0) {
                    RentLevelOperations.increaseLevel(current, delta, rules.maximumRentLevel)
                } else {
                    RentLevelOperations.decreaseLevel(current, -delta, rules.minimumRentLevel)
                }
            }
        if (changes.isEmpty()) {
            return EventResult.success(session, emptyList())
        }
        val updatedProperties = RentLevelOperations.applyRentLevelChanges(session.properties, changes)
        var updatedSession = session.copy(properties = updatedProperties)
        val (tx, sessionAfter) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
            propertyId = propertyId,
        )
        return EventResult.success(sessionAfter, listOf(tx))
    }

    private fun handleTotalGridlock(
        session: GameSession,
        eventId: String,
        timestamp: Long,
    ): EventResult {
        val nonJailedPlayers = session.players.values
            .filter { !it.jailStatus && !it.bankrupt }
            .map { it.playerId }
        val physical = PhysicalAction(
            instruction = "All non-jailed players move tokens to Free Parking. Do not pass GO. Jail players remain.",
            affectedPlayerIds = nonJailedPlayers,
            targetSpace = "FREE_PARKING",
        )
        val (tx, sessionAfter) = transactionFactory.create(
            session = session,
            type = TransactionType.EVENT_APPLIED,
            timestamp = timestamp,
            eventId = eventId,
        )
        return EventResult.success(sessionAfter, listOf(tx), physicalActions = listOf(physical))
    }

    data class EventResult(
        val session: GameSession?,
        val transactions: List<Transaction>,
        val physicalActions: List<PhysicalAction> = emptyList(),
        val pendingMessage: String? = null,
        val needsDebtResolution: Boolean = false,
        val error: String? = null,
    ) {
        companion object {
            fun success(
                session: GameSession,
                transactions: List<Transaction>,
                physicalActions: List<PhysicalAction> = emptyList(),
                pendingMessage: String? = null,
                needsDebtResolution: Boolean = false,
            ) = EventResult(session, transactions, physicalActions, pendingMessage, needsDebtResolution, null)

            fun failure(message: String) = EventResult(null, emptyList(), error = message)
        }

        val isSuccess: Boolean get() = session != null
    }
}
