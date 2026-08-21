package com.boardbanker.core.edition

import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.validation.GameDefinitionLoader

interface EditionFileSource {
    fun readCommon(fileName: String): String
    fun readEdition(editionId: String, fileName: String): String
}

class EditionRepository(
    private val files: EditionFileSource,
    private val loader: GameDefinitionLoader = GameDefinitionLoader(),
) {
    private val cache = mutableMapOf<String, GameDefinitions>()

    fun load(editionId: String = EditionIds.DEFAULT): GameDefinitions {
        val id = EditionIds.normalize(editionId)
        return cache.getOrPut(id) {
            val manifest = loader.loadEditionManifest(files.readEdition(id, "edition.json"))
            require(manifest.editionId == id) {
                "edition.json editionId '${manifest.editionId}' does not match requested '$id'"
            }
            loader.loadAll(
                cardsJson = files.readCommon("card_registry.json"),
                propertiesJson = files.readEdition(id, manifest.data.properties),
                eventsJson = files.readEdition(id, manifest.data.events),
                eventEngineRulesJson = files.readCommon("event_engine_rules.json"),
                boardRelationshipsJson = files.readEdition(id, manifest.data.boardRelationships),
                gameRulesJson = files.readCommon("game_rules.json"),
                bankingValuesJson = files.readEdition(id, manifest.data.bankingValues),
                edition = manifest,
            )
        }
    }

    fun loadManifest(editionId: String): EditionDefinition =
        loader.loadEditionManifest(files.readEdition(EditionIds.normalize(editionId), "edition.json"))
}
