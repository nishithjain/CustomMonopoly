package com.boardbanker.core.validation

import com.boardbanker.core.model.AuctionNoBidsBehaviour
import com.boardbanker.core.model.DebtPropertyValuation
import com.boardbanker.core.model.DebtResolutionMode
import com.boardbanker.core.model.EventActionType
import com.boardbanker.core.model.EventEngineRulesConfig
import com.boardbanker.core.model.EventIncompleteActionBehaviour
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameRules
import com.boardbanker.core.model.GoMovementCollectMode
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.WinnerDeterminationMode
import com.boardbanker.core.model.WinnerEndCondition
import com.boardbanker.core.model.WinnerTieBreaker

object GameRulesValidator {
    fun validate(rules: GameRules, editionId: String): List<String> {
        val problems = mutableListOf<String>()
        val prefix = "Edition '$editionId' game_rules.json"

        if (rules.setup.minimumPlayers <= 0) {
            problems += "$prefix setup.minimumPlayers must be > 0 (found ${rules.setup.minimumPlayers})"
        }
        if (rules.setup.maximumPlayers < rules.setup.minimumPlayers) {
            problems += "$prefix setup.maximumPlayers must be >= minimumPlayers " +
                "(found ${rules.setup.maximumPlayers} < ${rules.setup.minimumPlayers})"
        }
        if (rules.rent.minimumRentLevel <= 0) {
            problems += "$prefix rent.minimumRentLevel must be > 0"
        }
        if (rules.rent.maximumRentLevel < rules.rent.minimumRentLevel) {
            problems += "$prefix rent.maximumRentLevel must be >= rent.minimumRentLevel"
        }
        if (rules.colourSets.enabled && rules.colourSets.singleOwnerBonus < 0) {
            problems += "$prefix colourSets.singleOwnerBonus must be >= 0"
        }
        if (rules.colourSets.enabled && rules.colourSets.multiOwnerBonus < 0) {
            problems += "$prefix colourSets.multiOwnerBonus must be >= 0"
        }
        if (rules.undo.supported && rules.undo.undoDepth < 0) {
            problems += "$prefix undo.undoDepth must be >= 0"
        }
        if (rules.jail.exitByDoublesMaxAttempts < 0) {
            problems += "$prefix jail.exitByDoublesMaxAttempts must be >= 0"
        }
        if (rules.auction.timedAuctionSeconds <= 0) {
            problems += "$prefix auction.timedAuctionSeconds must be > 0"
        }
        if (rules.auction.winnerInitialRentLevel < rules.rent.minimumRentLevel ||
            rules.auction.winnerInitialRentLevel > rules.rent.maximumRentLevel
        ) {
            problems += "$prefix auction.winnerInitialRentLevel must be within rent level bounds"
        }
        validateEnum(prefix, "debt.resolutionMode", rules.debt.resolutionMode.name, DebtResolutionMode.entries.map { it.name }, problems)
        validateEnum(prefix, "debt.propertyValuation", rules.debt.propertyValuation.name, DebtPropertyValuation.entries.map { it.name }, problems)
        validateEnum(prefix, "auction.noBidsBehaviour", rules.auction.noBidsBehaviour.name, AuctionNoBidsBehaviour.entries.map { it.name }, problems)
        validateEnum(prefix, "winner.endCondition", rules.winner.endCondition.name, WinnerEndCondition.entries.map { it.name }, problems)
        validateEnum(prefix, "winner.winnerDetermination", rules.winner.winnerDetermination.name, WinnerDeterminationMode.entries.map { it.name }, problems)
        validateEnum(prefix, "winner.tieBreaker", rules.winner.tieBreaker.name, WinnerTieBreaker.entries.map { it.name }, problems)
        if (rules.winner.wealthUsesRentLevel) {
            problems += "$prefix winner.wealthUsesRentLevel=true is not supported by the current winner calculator"
        }
        if (!rules.go.suppressGoForTotalGridlock) {
            problems += "$prefix go.suppressGoForTotalGridlock=false is not supported by the current GoPolicy integration"
        }
        if (!rules.eventEngine.ownedPropertiesOnlyForRentChanges) {
            problems += "$prefix eventEngine.ownedPropertiesOnlyForRentChanges=false is not supported by the current event engine"
        }
        if (rules.eventEngine.incompleteActionBehaviour == EventIncompleteActionBehaviour.FAIL) {
            problems += "$prefix eventEngine.incompleteActionBehaviour=FAIL is not supported by the current event engine"
        }
        validateGoModes(prefix, rules.eventEngine, rules.go, problems)
        validateUndoTypes(prefix, rules, problems)
        return problems
    }

    fun validateAgainstEdition(rules: GameRules, definitions: GameDefinitions): List<String> {
        val problems = validate(rules, definitions.editionId).toMutableList()
        val rentLevelsPerProperty = definitions.edition?.cardConfiguration?.rentLevelsPerProperty
        if (rentLevelsPerProperty != null && rules.rent.maximumRentLevel != rentLevelsPerProperty) {
            problems += "Edition '${definitions.editionId}' game_rules.json rent.maximumRentLevel " +
                "(${rules.rent.maximumRentLevel}) must match edition cardConfiguration.rentLevelsPerProperty ($rentLevelsPerProperty)"
        }
        return problems
    }

    private fun validateGoModes(
        prefix: String,
        eventEngine: EventEngineRulesConfig,
        go: com.boardbanker.core.model.GoRulesConfig,
        problems: MutableList<String>,
    ) {
        listOf(
            "go.normalDiceMovementCollectsGo" to go.normalDiceMovementCollectsGo,
            "go.eventMovementCollectsGo" to go.eventMovementCollectsGo,
            "go.locationMovementCollectsGo" to go.locationMovementCollectsGo,
            "go.goToJailMovementCollectsGo" to go.goToJailMovementCollectsGo,
            "go.threeDoublesJailMovementCollectsGo" to go.threeDoublesJailMovementCollectsGo,
        ).forEach { (field, value) ->
            validateEnum(prefix, field, value.name, GoMovementCollectMode.entries.map { it.name }, problems)
        }
        validateEnum(
            prefix,
            "eventEngine.incompleteActionBehaviour",
            eventEngine.incompleteActionBehaviour.name,
            EventIncompleteActionBehaviour.entries.map { it.name },
            problems,
        )
    }

    private fun validateUndoTypes(prefix: String, rules: GameRules, problems: MutableList<String>) {
        val knownTypes = TransactionType.entries.map { it.name }.toSet()
        rules.undo.eligibleTransactionTypes.forEach { type ->
            if (type !in knownTypes) {
                problems += "$prefix undo.eligibleTransactionTypes contains unknown value '$type'"
            }
        }
        rules.undo.ineligibleTransactionTypes.forEach { type ->
            if (type !in knownTypes) {
                problems += "$prefix undo.ineligibleTransactionTypes contains unknown value '$type'"
            }
        }
    }

    private fun validateEnum(
        prefix: String,
        field: String,
        value: String,
        supported: List<String>,
        problems: MutableList<String>,
    ) {
        if (value !in supported) {
            problems += "$prefix $field has invalid value '$value' (supported: ${supported.joinToString()})"
        }
    }
}

object EventActionValidator {
    fun validateActionTypes(
        actions: List<com.boardbanker.core.model.EventActionDefinition>,
        editionId: String,
        eventId: String,
    ): List<String> {
        if (actions.isEmpty()) {
            return listOf("Edition '$editionId' event '$eventId' must define at least one action")
        }
        val problems = mutableListOf<String>()
        actions.forEachIndexed { index, action ->
            val path = "Edition '$editionId' event '$eventId' actions[$index]"
            try {
                EventActionType.valueOf(action.actionType)
            } catch (_: IllegalArgumentException) {
                problems += "$path.actionType '${action.actionType}' is unsupported (supported: ${EventActionType.entries.joinToString { it.name }})"
            }
            try {
                com.boardbanker.core.model.EventTargetType.valueOf(action.targetType)
            } catch (_: IllegalArgumentException) {
                problems += "$path.targetType '${action.targetType}' is unsupported"
            }
        }
        return problems
    }

    fun validateAgainstEdition(definitions: GameDefinitions): List<String> {
        val problems = mutableListOf<String>()
        definitions.events.values.forEach { event ->
            problems += validateActionTypes(event.actions, definitions.editionId, event.eventId)
            event.actions.forEachIndexed { index, action ->
                problems += validateActionTargets(definitions, event.eventId, index, action)
            }
        }
        return problems
    }

    private fun validateActionTargets(
        definitions: GameDefinitions,
        eventId: String,
        actionIndex: Int,
        action: com.boardbanker.core.model.EventActionDefinition,
    ): List<String> {
        val path = "Edition '${definitions.editionId}' event '$eventId' actions[$actionIndex] actionType '${action.actionType}'"
        val problems = mutableListOf<String>()
        when (action.actionType) {
            "SET_PROPERTY_RENT_LEVEL", "INCREASE_COLOR_SET_RENT_LEVEL", "DECREASE_COLOR_SET_RENT_LEVEL",
            "RESET_PROPERTY_RENT_LEVEL", "MOVE_THEN_PROPERTY_CHOICE", "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS",
            "DECREASE_BOARD_SIDE_RENT_LEVEL", "INCREASE_BOARD_SIDE_RENT_LEVEL",
            -> if (action.requiresPropertyScan && action.targetType.contains("PROPERTY")) {
                // scan-time validation only
            }
        }
        action.amount?.let { amount ->
            if (amount < 0 && action.actionType !in setOf("DECREASE_BOARD_SIDE_RENT_LEVEL")) {
                problems += "$path amount '$amount' is invalid"
            }
            if (action.actionType == "SET_PROPERTY_RENT_LEVEL" &&
                (amount < definitions.rules.minimumRentLevel || amount > definitions.rules.maximumRentLevel)
            ) {
                problems += "$path amount '$amount' is outside rent level bounds"
            }
        }
        if (!action.ownedPropertiesOnly) {
            problems += "$path ownedPropertiesOnly=false is not supported by the current event engine"
        }
        return problems
    }
}
