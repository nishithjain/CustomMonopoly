package com.boardbanker.core.validation

import com.boardbanker.core.card.CardDefinition
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.BankingValues
import com.boardbanker.core.model.EditionCatalog
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.BoardLayout
import com.boardbanker.core.model.BoardSpace
import com.boardbanker.core.model.BoardSpaceType
import com.boardbanker.core.model.BoardRelationships
import com.boardbanker.core.model.EnergyGridDefinition
import com.boardbanker.core.model.EnergyGridRentLevel
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameRules
import com.boardbanker.core.model.PlayerDefinition
import com.boardbanker.core.model.PropertyDefinition
import com.boardbanker.core.model.RentLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class GameDefinitionLoader(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun loadEditionManifest(jsonString: String): EditionDefinition =
        json.decodeFromString(EditionDefinition.serializer(), jsonString)

    fun loadEditionCatalog(jsonString: String): EditionCatalog =
        json.decodeFromString(EditionCatalog.serializer(), jsonString)

    fun loadGameRules(jsonString: String): GameRules =
        json.decodeFromString(GameRules.serializer(), jsonString)

    @Deprecated("Use loadGameRules", ReplaceWith("loadGameRules(jsonString)"))
    fun loadGameRulesConfig(jsonString: String): GameRules = loadGameRules(jsonString)

    fun loadBoardLayout(jsonString: String): BoardLayout {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val spaces = root["spaces"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            BoardSpace(
                position = obj["position"]!!.jsonPrimitive.content.toInt(),
                spaceId = obj["spaceId"]!!.jsonPrimitive.content,
                spaceType = BoardSpaceType.valueOf(obj["spaceType"]!!.jsonPrimitive.content),
                targetId = obj["targetId"]?.jsonPrimitive?.content,
                deckId = obj["deckId"]?.jsonPrimitive?.content,
            )
        }
        return BoardLayout(
            schemaVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toInt() ?: 1,
            spaces = spaces,
        )
    }

    fun mergeCardRegistries(commonCardsJson: String, editionCardsJson: String): List<CardDefinition> {
        val commonCards = loadCards(commonCardsJson)
        val editionCards = loadCards(editionCardsJson)
        val combined = commonCards + editionCards
        val duplicateIds = combined.groupBy { it.cardId }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "Duplicate card IDs across common and edition registries: ${duplicateIds.joinToString()}",
            )
        }
        val duplicatePayloads = combined.groupBy { it.qrPayload }.filter { it.value.size > 1 }.keys
        if (duplicatePayloads.isNotEmpty()) {
            throw IllegalArgumentException(
                "Duplicate QR payloads across common and edition registries: ${duplicatePayloads.joinToString()}",
            )
        }
        return combined
    }

    fun loadBankingValues(jsonString: String): BankingValues =
        json.decodeFromString(BankingValues.serializer(), jsonString)

    fun loadBoardRelationships(jsonString: String): BoardRelationships {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val colorGroups = root["colorGroups"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { it.jsonPrimitive.content }
        }
        val neighboursRoot = root["neighbours"]!!.jsonObject
        val neighbours = neighboursRoot["mappings"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { it.jsonPrimitive.content }
        }
        val boardSidesRoot = root["boardSides"]!!.jsonObject
        val boardSides = boardSidesRoot["mappings"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { it.jsonPrimitive.content }
        }
        val propertyToSide = boardSidesRoot["propertyToSide"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonPrimitive.content
        }
        return BoardRelationships(colorGroups, neighbours, boardSides, propertyToSide)
    }

    fun loadProperties(jsonString: String): List<PropertyDefinition> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["properties"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            PropertyDefinition(
                propertyId = obj["propertyId"]!!.jsonPrimitive.content,
                name = obj["name"]!!.jsonPrimitive.content,
                qrPayload = obj["qrPayload"]!!.jsonPrimitive.content,
                colorGroup = obj["colorGroup"]!!.jsonPrimitive.content,
                purchasePrice = obj["purchasePrice"]!!.jsonPrimitive.content.toInt(),
                initialRentLevel = obj["initialRentLevel"]!!.jsonPrimitive.content.toInt(),
                rentLevels = obj["rentLevels"]!!.jsonArray.map { rl ->
                    val r = rl.jsonObject
                    RentLevel(
                        level = r["level"]!!.jsonPrimitive.content.toInt(),
                        amount = r["amount"]!!.jsonPrimitive.content.toInt(),
                    )
                },
                maximumRentLevel = obj["maximumRentLevel"]?.jsonPrimitive?.content?.toInt() ?: 5,
            )
        }
    }

    fun loadEventEngineRuleEntries(jsonString: String): Map<String, List<EventActionDefinition>> =
        loadEventEngineRuleEntryList(jsonString).toMap()

    private fun loadEventEngineRuleEntryList(jsonString: String): List<Pair<String, List<EventActionDefinition>>> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["events"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            val eventId = obj["eventId"]!!.jsonPrimitive.content
            eventId to loadEventActionsForRule(element)
        }
    }

    private fun loadEventActionsForRule(element: kotlinx.serialization.json.JsonElement): List<EventActionDefinition> {
        val obj = element.jsonObject
        val actionsElement = obj["actions"]
        return if (actionsElement != null) {
            actionsElement.jsonArray.map { actionElement ->
                json.decodeFromJsonElement(EventActionDefinition.serializer(), actionElement)
            }
        } else {
            listOf(json.decodeFromJsonElement(EventActionDefinition.serializer(), element))
        }
    }

    fun loadEvents(jsonString: String, engineRuleEntries: Map<String, List<EventActionDefinition>>): List<EventDefinition> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["events"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            val eventId = obj["eventId"]!!.jsonPrimitive.content
            val actions = engineRuleEntries[eventId]
                ?: throw IllegalArgumentException("Missing engine rule for $eventId")
            if (actions.isEmpty()) {
                throw IllegalArgumentException("Event $eventId must define at least one action")
            }
            EventDefinition(
                eventId = eventId,
                deckId = obj["deckId"]?.jsonPrimitive?.content ?: "main",
                name = obj["name"]!!.jsonPrimitive.content,
                qrPayload = obj["qrPayload"]!!.jsonPrimitive.content,
                eventSubtitle = obj["eventSubtitle"]?.jsonPrimitive?.content ?: "",
                eventDescription = obj["eventDescription"]?.jsonPrimitive?.content
                    ?: obj["printedText"]?.jsonPrimitive?.content
                    ?: "",
                actions = actions,
            )
        }
    }

    fun loadCards(jsonString: String): List<CardDefinition> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["cards"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            CardDefinition(
                cardId = obj["cardId"]!!.jsonPrimitive.content,
                cardType = CardType.valueOf(obj["cardType"]!!.jsonPrimitive.content),
                name = obj["name"]!!.jsonPrimitive.content,
                qrPayload = obj["qrPayload"]!!.jsonPrimitive.content,
            )
        }
    }

    fun loadPlayersFromCards(cards: List<CardDefinition>): List<PlayerDefinition> =
        cards
            .filter { it.cardType == CardType.USER }
            .map { card ->
                PlayerDefinition(
                    playerId = card.cardId,
                    qrPayload = card.qrPayload,
                    displayName = card.name,
                    displayColor = card.name,
                )
            }

    fun loadEnergyGrids(jsonString: String): List<EnergyGridDefinition> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["energyGrids"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            EnergyGridDefinition(
                energyGridId = obj["energyGridId"]!!.jsonPrimitive.content,
                name = obj["name"]!!.jsonPrimitive.content,
                sequence = obj["sequence"]!!.jsonPrimitive.content.toInt(),
                qrPayload = obj["qrPayload"]!!.jsonPrimitive.content,
                frontAsset = obj["frontAsset"]!!.jsonPrimitive.content,
                qrAsset = obj["qrAsset"]!!.jsonPrimitive.content,
                purchasePrice = obj["purchasePrice"]!!.jsonPrimitive.content.toInt(),
                rentLevels = obj["rentLevels"]!!.jsonArray.map { rl ->
                    val r = rl.jsonObject
                    EnergyGridRentLevel(
                        ownedCount = r["ownedCount"]!!.jsonPrimitive.content.toInt(),
                        amount = r["amount"]!!.jsonPrimitive.content.toInt(),
                    )
                },
            )
        }
    }

    fun loadAll(
        commonCardsJson: String,
        editionCardsJson: String,
        propertiesJson: String,
        eventsJson: String,
        eventEngineRulesJson: String,
        boardRelationshipsJson: String,
        boardLayoutJson: String,
        gameRulesJson: String,
        bankingValuesJson: String,
        edition: EditionDefinition? = null,
        energyGridsJson: String? = null,
    ): GameDefinitions {
        val rules = loadGameRules(gameRulesJson)
        val bankingValues = loadBankingValues(bankingValuesJson)
        val engineRuleEntries = loadEventEngineRuleEntries(eventEngineRulesJson)
        val properties = loadProperties(propertiesJson)
        val energyGrids = energyGridsJson?.let { loadEnergyGrids(it) } ?: emptyList()
        val events = loadEvents(eventsJson, engineRuleEntries)
        val cards = overlayEditionCardNames(
            mergeCardRegistries(commonCardsJson, editionCardsJson),
            properties,
            events,
            energyGrids,
        )
        val resolvedEdition = edition ?: EditionDefinition(
            editionId = EditionIds.LEGACY_EDITION_ID,
            definitionVersion = EditionIds.LEGACY_DEFINITION_VERSION,
            name = "UK Edition",
            countryCode = "GB",
            currency = bankingValues.currency,
        )
        val cardConfigProblems = CardConfigurationValidator.validate(resolvedEdition)
        if (cardConfigProblems.isNotEmpty()) {
            throw IllegalArgumentException(cardConfigProblems.joinToString("; "))
        }
        val versionProblems = DefinitionVersionValidator.validate(resolvedEdition)
        if (versionProblems.isNotEmpty()) {
            throw IllegalArgumentException(versionProblems.joinToString("; "))
        }
        val definitions = GameDefinitions(
            editionId = resolvedEdition.editionId,
            edition = resolvedEdition,
            cards = cards.associateBy { it.cardId },
            cardsByQrPayload = cards.associateBy { it.qrPayload },
            players = loadPlayersFromCards(cards).associateBy { it.playerId },
            properties = properties.associateBy { it.propertyId },
            energyGrids = energyGrids.associateBy { it.energyGridId },
            events = events.associateBy { it.eventId },
            boardRelationships = loadBoardRelationships(boardRelationshipsJson),
            boardLayout = loadBoardLayout(boardLayoutJson),
            rules = rules,
            bankingValues = bankingValues,
        )
        val ruleProblems = GameRulesValidator.validateAgainstEdition(rules, definitions) +
            EventActionValidator.validateAgainstEdition(definitions)
        if (ruleProblems.isNotEmpty()) {
            throw IllegalArgumentException(ruleProblems.joinToString("; "))
        }
        val problems = DefinitionValidator().validate(definitions)
        if (problems.isNotEmpty()) {
            throw IllegalArgumentException(
                "GameDefinitions validation FAIL: ${problems.joinToString("; ")}",
            )
        }
        return definitions
    }

    private fun overlayEditionCardNames(
        cards: List<CardDefinition>,
        properties: List<PropertyDefinition>,
        events: List<EventDefinition>,
        energyGrids: List<EnergyGridDefinition> = emptyList(),
    ): List<CardDefinition> {
        val propertyNames = properties.associate { it.propertyId to it.name }
        val eventNames = events.associate { it.eventId to it.name }
        val energyGridNames = energyGrids.associate { it.energyGridId to it.name }
        return cards.map { card ->
            val overlay = when (card.cardType) {
                CardType.PROPERTY -> propertyNames[card.cardId]
                CardType.EVENT -> eventNames[card.cardId]
                CardType.ENERGY_GRID -> energyGridNames[card.cardId]
                CardType.USER -> null
            }
            if (overlay == null) card else card.copy(name = overlay)
        }
    }
}
