package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GameRulesConfig(
    val schemaVersion: Int = 1,
    val setup: JsonObject = JsonObject(emptyMap()),
    val banking: JsonObject = JsonObject(emptyMap()),
    val properties: JsonObject = JsonObject(emptyMap()),
    val colorSetBonus: JsonObject = JsonObject(emptyMap()),
    val rent: JsonObject = JsonObject(emptyMap()),
    val go: JsonObject = JsonObject(emptyMap()),
    val jail: JsonObject = JsonObject(emptyMap()),
    val locationSpaces: JsonObject = JsonObject(emptyMap()),
    val auction: JsonObject = JsonObject(emptyMap()),
    val debt: JsonObject = JsonObject(emptyMap()),
    val bankruptcy: JsonObject = JsonObject(emptyMap()),
    val endGame: JsonObject = JsonObject(emptyMap()),
    val undo: JsonObject = JsonObject(emptyMap()),
    val temporaryEffects: JsonObject = JsonObject(emptyMap()),
) {
    val minimumPlayers: Int get() = setup.intValue("minimumPlayers", 2)
    val maximumPlayers: Int get() = setup.intValue("maximumPlayers", 4)
    val singleOwnerColorBonus: Int get() = colorSetBonus.intValue("singleOwnerBonus", 2)
    val multiOwnerColorBonus: Int get() = colorSetBonus.intValue("multiOwnerBonus", 1)
    val maximumRentLevel: Int get() = rent.intValue("maximumRentLevel", 5)
    val minimumRentLevel: Int get() = rent.intValue("minimumRentLevel", 1)
    val undoDepth: Int get() = undo.intValue("undoDepth", 1)

    private fun JsonObject.intValue(key: String, default: Int): Int {
        val element = this[key]
        return element?.toString()?.toIntOrNull() ?: default
    }
}
