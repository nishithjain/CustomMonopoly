package com.boardbanker.core.persistence

import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameMetadataReaderTest {
    private val reader = SavedGameMetadataReader()

    @Test
    fun readsEditionIdAndDefinitionVersionFromJson() {
        val metadata = readSuccess(
            saveSchemaVersion = GameSessionSchema.CURRENT_VERSION,
            json = """
            {
              "gameId": "META_TEST",
              "editionId": "india",
              "editionDefinitionVersion": 1
            }
            """.trimIndent(),
        )
        assertEquals("india", metadata.editionId)
        assertEquals(1, metadata.editionDefinitionVersion)
    }

    @Test
    fun recognizedLegacySaveWithoutEditionFieldsUsesUkVersionOne() {
        val metadata = readSuccess(
            saveSchemaVersion = GameSessionSchema.LEGACY_PRE_EDITION_VERSION,
            json = """
            {
              "gameId": "LEGACY"
            }
            """.trimIndent(),
        )
        assertEquals(EditionIds.LEGACY_EDITION_ID, metadata.editionId)
        assertEquals(EditionIds.LEGACY_DEFINITION_VERSION, metadata.editionDefinitionVersion)
    }

    @Test
    fun currentFormatMissingEditionIdIsCorrupted() {
        val result = reader.read(
            """
            {
              "gameId": "BAD",
              "editionDefinitionVersion": 1
            }
            """.trimIndent(),
            GameSessionSchema.CURRENT_VERSION,
        )
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    @Test
    fun currentFormatMissingBothEditionFieldsIsCorrupted() {
        val result = reader.read(
            """
            {
              "gameId": "BAD"
            }
            """.trimIndent(),
            GameSessionSchema.CURRENT_VERSION,
        )
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    @Test
    fun currentFormatMissingEditionDefinitionVersionIsCorrupted() {
        val result = reader.read(
            """
            {
              "gameId": "BAD",
              "editionId": "uk"
            }
            """.trimIndent(),
            GameSessionSchema.CURRENT_VERSION,
        )
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    @Test
    fun legacySaveWithoutEditionDefinitionVersionUsesOne() {
        val metadata = readSuccess(
            saveSchemaVersion = GameSessionSchema.LEGACY_PRE_EDITION_VERSION,
            json = """
            {
              "gameId": "LEGACY",
              "editionId": "uk"
            }
            """.trimIndent(),
        )
        assertEquals(EditionIds.LEGACY_DEFINITION_VERSION, metadata.editionDefinitionVersion)
    }

    @Test
    fun blankEditionIdIsMalformed() {
        val result = reader.read(
            """{"gameId":"BAD","editionId":""}""",
            GameSessionSchema.CURRENT_VERSION,
        )
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    @Test
    fun nonIntegerDefinitionVersionIsMalformed() {
        val result = reader.read(
            """{"gameId":"BAD","editionId":"uk","editionDefinitionVersion":"one"}""",
            GameSessionSchema.CURRENT_VERSION,
        )
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    @Test
    fun invalidJsonIsCorrupted() {
        val result = reader.read("{not-json", GameSessionSchema.CURRENT_VERSION)
        assertTrue(result is SavedGameMetadataReadResult.Corrupted)
    }

    private fun readSuccess(saveSchemaVersion: Int, json: String): SavedGameMetadata {
        val result = reader.read(json, saveSchemaVersion)
        assertTrue(result is SavedGameMetadataReadResult.Success)
        return (result as SavedGameMetadataReadResult.Success).metadata
    }
}
