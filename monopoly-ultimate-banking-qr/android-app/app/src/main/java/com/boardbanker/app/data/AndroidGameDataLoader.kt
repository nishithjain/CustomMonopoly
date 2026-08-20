package com.boardbanker.app.data

import android.content.Context
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.validation.GameDefinitionLoader

/**
 * Reads generated runtime assets and delegates parsing to :game-core.
 */
class AndroidGameDataLoader(
    private val context: Context,
) {
    fun load(): GameDefinitions {
        val loader = GameDefinitionLoader()
        val assets = context.assets
        return loader.loadAll(
            cardsJson = assets.open("game/cards.json").bufferedReader().use { it.readText() },
            propertiesJson = assets.open("game/properties.json").bufferedReader().use { it.readText() },
            eventsJson = assets.open("game/events.json").bufferedReader().use { it.readText() },
            eventEngineRulesJson = assets.open("game/event_engine_rules.json").bufferedReader().use { it.readText() },
            boardRelationshipsJson = assets.open("game/board_relationships.json").bufferedReader().use { it.readText() },
            gameRulesJson = assets.open("game/game_rules.json").bufferedReader().use { it.readText() },
        )
    }
}
