package com.boardbanker.core.model

import com.boardbanker.core.card.CardDefinition

import com.boardbanker.core.rules.policy.GameRulePolicies

data class GameDefinitions(
    val editionId: String,
    val edition: EditionDefinition? = null,
    val cards: Map<String, CardDefinition>,
    val cardsByQrPayload: Map<String, CardDefinition>,
    val players: Map<String, PlayerDefinition>,
    val properties: Map<String, PropertyDefinition>,
    val energyGrids: Map<String, EnergyGridDefinition> = emptyMap(),
    val events: Map<String, EventDefinition>,
    val boardRelationships: BoardRelationships,
    val boardLayout: BoardLayout,
    val rules: GameRules,
    val bankingValues: BankingValues,
) {
    val policies: GameRulePolicies by lazy { GameRulePolicies(rules) }
}
