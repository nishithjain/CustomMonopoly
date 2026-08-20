package com.boardbanker.core.validation

import com.boardbanker.core.card.CardDefinition
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.BoardRelationships
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.EventEngineRule
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameRulesConfig
import com.boardbanker.core.model.PlayerDefinition
import com.boardbanker.core.model.PropertyDefinition
import com.boardbanker.core.model.RentLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GameDefinitionLoader(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun loadGameRulesConfig(jsonString: String): GameRulesConfig =
        json.decodeFromString(GameRulesConfig.serializer(), jsonString)

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
                printedText = obj["printedText"]?.jsonPrimitive?.content ?: "",
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
    ): GameDefinitions {
        val rulesConfig = loadGameRulesConfig(gameRulesJson)
        val engineRules = loadEventEngineRules(eventEngineRulesJson)
        val cards = loadCards(cardsJson)
        return GameDefinitions(
            cards = cards.associateBy { it.cardId },
            cardsByQrPayload = cards.associateBy { it.qrPayload },
            players = loadPlayersFromCards(cardsJson).associateBy { it.playerId },
            properties = loadProperties(propertiesJson).associateBy { it.propertyId },
            events = loadEvents(eventsJson, engineRules).associateBy { it.eventId },
            boardRelationships = loadBoardRelationships(boardRelationshipsJson),
            rulesConfig = rulesConfig,
        )
    }
}
