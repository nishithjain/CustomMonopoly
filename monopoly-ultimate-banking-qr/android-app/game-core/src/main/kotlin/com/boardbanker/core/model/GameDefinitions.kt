package com.boardbanker.core.model

import com.boardbanker.core.card.CardDefinition

data class GameDefinitions(
    val cards: Map<String, CardDefinition>,
    val cardsByQrPayload: Map<String, CardDefinition>,
    val players: Map<String, PlayerDefinition>,
    val properties: Map<String, PropertyDefinition>,
    val events: Map<String, EventDefinition>,
    val boardRelationships: BoardRelationships,
    val rulesConfig: GameRulesConfig,
    val bankingValues: BankingValues,
)
