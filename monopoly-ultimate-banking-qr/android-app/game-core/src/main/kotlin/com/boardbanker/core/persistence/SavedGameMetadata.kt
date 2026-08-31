package com.boardbanker.core.persistence

import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SavedGameMetadata(
    val editionId: String,
    val editionDefinitionVersion: Int,
)

sealed class SavedGameMetadataReadResult {
    data class Success(val metadata: SavedGameMetadata) : SavedGameMetadataReadResult()
    data class Corrupted(val reason: String) : SavedGameMetadataReadResult()
}

class SavedGameMetadataReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun read(sessionJson: String, saveSchemaVersion: Int): SavedGameMetadataReadResult {
        val root = try {
            json.parseToJsonElement(sessionJson).jsonObject
        } catch (ex: Exception) {
            return SavedGameMetadataReadResult.Corrupted(ex.message ?: "Invalid session JSON")
        }

        val isLegacyFormat = saveSchemaVersion <= GameSessionSchema.LEGACY_PRE_EDITION_VERSION

        val editionId = readEditionId(root, isLegacyFormat)
            ?: return SavedGameMetadataReadResult.Corrupted("Saved game metadata is malformed: editionId is blank or invalid.")

        val definitionVersion = readDefinitionVersion(root, isLegacyFormat)
            ?: return SavedGameMetadataReadResult.Corrupted(
                "Saved game metadata is malformed: editionDefinitionVersion is invalid.",
            )

        return SavedGameMetadataReadResult.Success(
            SavedGameMetadata(
                editionId = editionId,
                editionDefinitionVersion = definitionVersion,
            ),
        )
    }

    fun readGameStatus(sessionJson: String): GameStatus? {
        val statusValue = try {
            json.parseToJsonElement(sessionJson).jsonObject["status"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        } ?: return null
        return runCatching { GameStatus.valueOf(statusValue) }.getOrNull()
    }

    private fun readEditionId(root: JsonObject, isLegacyFormat: Boolean): String? {
        if (!root.containsKey("editionId")) {
            return if (isLegacyFormat) {
                EditionIds.LEGACY_EDITION_ID
            } else {
                null
            }
        }
        val editionElement = root["editionId"]
            ?: return null
        if (editionElement !is JsonPrimitive || !editionElement.isString) {
            return null
        }
        val raw = editionElement.content.trim()
        if (raw.isEmpty()) {
            return null
        }
        return raw
    }

    private fun readDefinitionVersion(root: JsonObject, isLegacyFormat: Boolean): Int? {
        if (!root.containsKey("editionDefinitionVersion")) {
            return if (isLegacyFormat) {
                EditionIds.LEGACY_DEFINITION_VERSION
            } else {
                null
            }
        }
        val versionElement = root["editionDefinitionVersion"]
            ?: return null
        if (versionElement !is JsonPrimitive) {
            return null
        }
        return versionElement.intOrNull
    }
}
