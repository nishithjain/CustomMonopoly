package com.boardbanker.core.validation

import com.boardbanker.core.card.CardDefinition
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.BankingValues
import com.boardbanker.core.model.BoardRelationships
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventEngineRule
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameRulesConfig
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

    fun loadGameRulesConfig(jsonString: String): GameRulesConfig =
        json.decodeFromString(GameRulesConfig.serializer(), jsonString)

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

    fun loadEventEngineRules(jsonString: String): List<EventEngineRule> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["events"]!!.jsonArray.map { element ->
            json.decodeFromJsonElement(EventEngineRule.serializer(), element)
        }
    }

    fun loadEvents(jsonString: String, engineRules: List<EventEngineRule>): List<EventDefinition> {
        val rulesById = engineRules.associateBy { it.eventId }
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["events"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            val eventId = obj["eventId"]!!.jsonPrimitive.content
            val rule = rulesById[eventId]
                ?: throw IllegalArgumentException("Missing engine rule for $eventId")
            EventDefinition(
                eventId = eventId,
                name = obj["name"]!!.jsonPrimitive.content,
                qrPayload = obj["qrPayload"]!!.jsonPrimitive.content,
                eventSubtitle = obj["eventSubtitle"]?.jsonPrimitive?.content ?: "",
                eventDescription = obj["eventDescription"]?.jsonPrimitive?.content
                    ?: obj["printedText"]?.jsonPrimitive?.content
                    ?: "",
                engineRule = rule,
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

    fun loadPlayersFromCards(jsonString: String): List<PlayerDefinition> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        return root["cards"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it["cardType"]!!.jsonPrimitive.content == "USER" }
            .map { card ->
                PlayerDefinition(
                    playerId = card["cardId"]!!.jsonPrimitive.content,
                    qrPayload = card["qrPayload"]!!.jsonPrimitive.content,
                    displayName = card["name"]!!.jsonPrimitive.content,
                    displayColor = card["name"]!!.jsonPrimitive.content,
                )
            }
    }

    fun loadAll(
        cardsJson: String,
        propertiesJson: String,
        eventsJson: String,
        eventEngineRulesJson: String,
        boardRelationshipsJson: String,
        gameRulesJson: String,
        bankingValuesJson: String,
        edition: EditionDefinition? = null,
    ): GameDefinitions {
        val rulesConfig = loadGameRulesConfig(gameRulesJson)
        val bankingValues = loadBankingValues(bankingValuesJson)
        val engineRules = loadEventEngineRules(eventEngineRulesJson)
        val properties = loadProperties(propertiesJson)
        val events = loadEvents(eventsJson, engineRules)
        val cards = overlayEditionCardNames(loadCards(cardsJson), properties, events)
        val resolvedEdition = edition ?: EditionDefinition(
            editionId = EditionIds.DEFAULT,
            name = "UK Edition",
            countryCode = "GB",
            currency = bankingValues.currency,
        )
        val definitions = GameDefinitions(
            editionId = resolvedEdition.editionId,
            edition = resolvedEdition,
            cards = cards.associateBy { it.cardId },
            cardsByQrPayload = cards.associateBy { it.qrPayload },
            players = loadPlayersFromCards(cardsJson).associateBy { it.playerId },
            properties = properties.associateBy { it.propertyId },
            events = events.associateBy { it.eventId },
            boardRelationships = loadBoardRelationships(boardRelationshipsJson),
            rulesConfig = rulesConfig,
            bankingValues = bankingValues,
        )
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
    ): List<CardDefinition> {
        val propertyNames = properties.associate { it.propertyId to it.name }
        val eventNames = events.associate { it.eventId to it.name }
        return cards.map { card ->
            val overlay = when (card.cardType) {
                CardType.PROPERTY -> propertyNames[card.cardId]
                CardType.EVENT -> eventNames[card.cardId]
                CardType.USER -> null
            }
            if (overlay == null) card else card.copy(name = overlay)
        }
    }
}
