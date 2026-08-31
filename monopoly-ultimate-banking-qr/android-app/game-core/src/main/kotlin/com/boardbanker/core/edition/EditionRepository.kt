package com.boardbanker.core.edition

import com.boardbanker.core.model.EditionCatalog
import com.boardbanker.core.model.EditionCatalogEntry
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.validation.CardConfigurationValidator
import com.boardbanker.core.validation.DefinitionVersionValidator
import com.boardbanker.core.validation.GameDefinitionLoader

interface EditionFileSource {
    fun readCommon(fileName: String): String
    fun readEdition(editionId: String, fileName: String): String
    fun readCatalogIndex(): String
}

class EditionRepository(
    private val files: EditionFileSource,
    private val loader: GameDefinitionLoader = GameDefinitionLoader(),
) {
    private val cache = mutableMapOf<String, GameDefinitions>()

    fun load(editionId: String): GameDefinitions {
        val id = EditionIds.normalize(editionId)
        val manifest = loader.loadEditionManifest(files.readEdition(id, "edition.json"))
        require(manifest.editionId == id) {
            "edition.json editionId '${manifest.editionId}' does not match requested '$id'"
        }
        val cacheKey = "$id@${manifest.definitionVersion}"
        return cache.getOrPut(cacheKey) {
            val eventEngineRulesFile = manifest.data.eventEngineRules ?: "event_engine_rules.json"
            val eventEngineRulesJson = if (manifest.data.eventEngineRules != null) {
                files.readEdition(id, eventEngineRulesFile)
            } else {
                files.readCommon(eventEngineRulesFile)
            }
            val gameRulesFile = manifest.data.gameRules ?: "game_rules.json"
            val gameRulesJson = if (manifest.data.gameRules != null) {
                files.readEdition(id, gameRulesFile)
            } else {
                files.readCommon("game_rules.json")
            }
            loader.loadAll(
                commonCardsJson = files.readCommon("card_registry.json"),
                editionCardsJson = files.readEdition(id, manifest.data.cardRegistry),
                propertiesJson = files.readEdition(id, manifest.data.properties),
                eventsJson = files.readEdition(id, manifest.data.events),
                eventEngineRulesJson = eventEngineRulesJson,
                boardRelationshipsJson = files.readEdition(id, manifest.data.boardRelationships),
                boardLayoutJson = files.readEdition(id, manifest.data.boardLayout),
                gameRulesJson = gameRulesJson,
                bankingValuesJson = files.readEdition(id, manifest.data.bankingValues),
                edition = manifest,
            )
        }
    }

    fun loadManifest(editionId: String): EditionDefinition {
        val manifest = loader.loadEditionManifest(files.readEdition(EditionIds.normalize(editionId), "edition.json"))
        val problems = DefinitionVersionValidator.validate(manifest) +
            CardConfigurationValidator.validate(manifest)
        if (problems.isNotEmpty()) {
            throw IllegalArgumentException(problems.joinToString("; "))
        }
        return manifest
    }

    fun loadEditionCatalog(): EditionCatalog {
        val path = CATALOG_PATH
        val jsonString = try {
            files.readCatalogIndex()
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to read edition catalogue at $path: ${ex.message}", ex)
        }
        val parsed = try {
            loader.loadEditionCatalog(jsonString)
        } catch (ex: Exception) {
            throw IllegalStateException("Malformed edition catalogue at $path: ${ex.message}", ex)
        }
        validateEditionCatalog(parsed, path)
        return EditionCatalog(
            defaultEditionId = parsed.defaultEditionId.trim(),
            editions = parsed.editions.filter { it.enabled },
        )
    }

    private fun validateEditionCatalog(catalog: EditionCatalog, path: String) {
        val defaultId = catalog.defaultEditionId.trim()
        require(defaultId.isNotEmpty()) {
            "Edition catalogue at $path has a blank defaultEditionId"
        }
        val seenIds = mutableSetOf<String>()
        catalog.editions.forEach { entry ->
            val editionId = entry.editionId.trim()
            val name = entry.name.trim()
            require(editionId.isNotEmpty()) {
                "Edition catalogue at $path contains a blank editionId"
            }
            require(name.isNotEmpty()) {
                "Edition catalogue at $path contains a blank name for edition '$editionId'"
            }
            require(seenIds.add(editionId)) {
                "Edition catalogue at $path contains duplicate editionId '$editionId'"
            }
        }
        val defaultEntry = catalog.editions.find { it.editionId.trim() == defaultId }
            ?: throw IllegalArgumentException(
                "Edition catalogue at $path defaultEditionId '$defaultId' is not listed in editions",
            )
        require(defaultEntry.enabled) {
            "Edition catalogue at $path defaultEditionId '$defaultId' is disabled"
        }
    }

    companion object {
        const val CATALOG_PATH = "data/editions/index.json"
    }
}
