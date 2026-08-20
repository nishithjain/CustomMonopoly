package com.boardbanker.core.validation

import com.boardbanker.core.model.GameDefinitions

class DefinitionValidator {

    fun validate(definitions: GameDefinitions): List<String> {
        val problems = mutableListOf<String>()

        if (definitions.players.isEmpty()) {
            problems += "No player definitions loaded"
        }
        if (definitions.properties.size != 22) {
            problems += "Expected 22 properties, found ${definitions.properties.size}"
        }
        if (definitions.events.size != 23) {
            problems += "Expected 23 events, found ${definitions.events.size}"
        }
        if (definitions.cards.size != 49) {
            problems += "Expected 49 cards, found ${definitions.cards.size}"
        }
        if (definitions.cardsByQrPayload.size != definitions.cards.size) {
            problems += "Duplicate QR payloads in card registry"
        }

        for (property in definitions.properties.values) {
            if (property.rentLevels.size != 5) {
                problems += "${property.propertyId}: expected 5 rent levels"
            }
            val colorGroup = definitions.boardRelationships.colorGroups[property.colorGroup]
            if (colorGroup == null) {
                problems += "${property.propertyId}: unknown color group ${property.colorGroup}"
            } else if (!colorGroup.contains(property.propertyId)) {
                problems += "${property.propertyId}: not in declared color group ${property.colorGroup}"
            }
        }

        for (event in definitions.events.values) {
            if (event.engineRule.eventId != event.eventId) {
                problems += "${event.eventId}: engine rule ID mismatch"
            }
        }

        for ((group, propertyIds) in definitions.boardRelationships.colorGroups) {
            for (propertyId in propertyIds) {
                if (!definitions.properties.containsKey(propertyId)) {
                    problems += "Color group $group references unknown property $propertyId"
                }
            }
        }

        for ((propertyId, neighbours) in definitions.boardRelationships.neighbours) {
            if (!definitions.properties.containsKey(propertyId)) {
                problems += "Neighbour mapping for unknown property $propertyId"
            }
            neighbours.forEach { neighbour ->
                if (!definitions.properties.containsKey(neighbour)) {
                    problems += "Neighbour $neighbour of $propertyId is unknown"
                }
            }
        }

        return problems
    }

    fun isValid(definitions: GameDefinitions): Boolean = validate(definitions).isEmpty()
}
