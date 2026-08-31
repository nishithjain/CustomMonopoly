package com.boardbanker.core

import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EditionCatalogTests {
    private val repository = EditionRepository(FileEditionFileSource(TestFixtures.dataDir))

    @Test
    fun loadsUkAndIndiaSuccessfully() {
        val catalog = repository.loadEditionCatalog()
        assertEquals("uk", catalog.defaultEditionId)
        assertEquals(listOf("uk", "india"), catalog.editions.map { it.editionId })
    }

    @Test
    fun preservesCatalogueOrder() {
        val catalog = repository.loadEditionCatalog()
        assertEquals("UK Edition", catalog.editions[0].name)
        assertEquals("India Edition", catalog.editions[1].name)
    }

    @Test
    fun returnsUkAsDefault() {
        val catalog = repository.loadEditionCatalog()
        assertEquals(EditionIds.UK, catalog.defaultEditionId)
    }

    @Test
    fun productionCatalogueExcludesCustomTestEdition() {
        val catalog = repository.loadEditionCatalog()
        assertFalse(catalog.editions.any { it.editionId == TestEditionResources.CUSTOM_TEST_EDITION_ID })
    }

    @Test
    fun filtersDisabledEditionsFromNewGameChoices() {
        val tempRoot = Files.createTempDirectory("edition-catalog-disabled").resolve("data")
        copyDataTree(tempRoot)
        tempRoot.resolve("editions/index.json").writeText(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true },
                { "editionId": "india", "name": "India Edition", "enabled": false }
              ]
            }
            """.trimIndent(),
        )
        val tempRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val catalog = tempRepository.loadEditionCatalog()
        assertEquals(listOf("uk"), catalog.editions.map { it.editionId })
    }

    @Test
    fun rejectsDuplicateEditionIds() {
        assertCatalogError(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true },
                { "editionId": "uk", "name": "Duplicate UK", "enabled": true }
              ]
            }
            """.trimIndent(),
            "duplicate editionId",
        )
    }

    @Test
    fun rejectsMissingDefaultEdition() {
        assertCatalogError(
            """
            {
              "defaultEditionId": "missing",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true }
              ]
            }
            """.trimIndent(),
            "defaultEditionId 'missing'",
        )
    }

    @Test
    fun rejectsDisabledDefaultEdition() {
        assertCatalogError(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": false }
              ]
            }
            """.trimIndent(),
            "defaultEditionId 'uk' is disabled",
        )
    }

    @Test
    fun rejectsBlankEditionId() {
        assertCatalogError(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": " ", "name": "Blank", "enabled": true }
              ]
            }
            """.trimIndent(),
            "blank editionId",
        )
    }

    @Test
    fun rejectsBlankEditionName() {
        assertCatalogError(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": " ", "enabled": true }
              ]
            }
            """.trimIndent(),
            "blank name",
        )
    }

    @Test
    fun rejectsBlankDefaultEditionId() {
        assertCatalogError(
            """
            {
              "defaultEditionId": " ",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true }
              ]
            }
            """.trimIndent(),
            "blank defaultEditionId",
        )
    }

    @Test
    fun reportsMalformedJsonClearly() {
        val tempRoot = Files.createTempDirectory("edition-catalog-malformed").resolve("data")
        copyDataTree(tempRoot)
        tempRoot.resolve("editions/index.json").writeText("{ not-json")
        val tempRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val error = runCatching { tempRepository.loadEditionCatalog() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("data/editions/index.json"))
        assertTrue(error.message!!.contains("Malformed edition catalogue"))
    }

    @Test
    fun thirdCatalogueEntryRequiresNoKotlinEditionList() {
        val tempRoot = Files.createTempDirectory("edition-catalog-custom").resolve("data")
        copyDataTree(tempRoot)
        val customEditionDir = tempRoot.resolve("editions/custom-edition")
        Files.createDirectories(customEditionDir)
        val ukEditionDir = TestFixtures.dataDir.resolve("editions/uk")
        listOf(
            "edition.json",
            "properties.json",
            "banking_values.json",
            "events.json",
            "board_relationships.json",
            "board_layout.json",
            "card_registry.json",
        ).forEach { fileName ->
            customEditionDir.resolve(fileName).writeText(ukEditionDir.resolve(fileName).readText())
        }
        customEditionDir.resolve("edition.json").writeText(
            customEditionDir.resolve("edition.json").readText()
                .replace("\"editionId\": \"uk\"", "\"editionId\": \"custom-edition\"")
                .replace("\"name\": \"UK Edition\"", "\"name\": \"Custom Edition\""),
        )
        tempRoot.resolve("editions/index.json").writeText(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true },
                { "editionId": "india", "name": "India Edition", "enabled": true },
                { "editionId": "custom-edition", "name": "Custom Edition", "enabled": true }
              ]
            }
            """.trimIndent(),
        )
        val tempRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val catalog = tempRepository.loadEditionCatalog()
        assertEquals(
            listOf("uk", "india", "custom-edition"),
            catalog.editions.map { it.editionId },
        )
        assertEquals("Custom Edition", tempRepository.loadManifest("custom-edition").name)
    }

    @Test
    fun disabledCatalogueEntryCanStillLoadEditionDataForResume() {
        val tempRoot = Files.createTempDirectory("edition-catalog-resume").resolve("data")
        copyDataTree(tempRoot)
        tempRoot.resolve("editions/index.json").writeText(
            """
            {
              "defaultEditionId": "uk",
              "editions": [
                { "editionId": "uk", "name": "UK Edition", "enabled": true },
                { "editionId": "india", "name": "India Edition", "enabled": false }
              ]
            }
            """.trimIndent(),
        )
        val tempRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val catalog = tempRepository.loadEditionCatalog()
        assertEquals(listOf("uk"), catalog.editions.map { it.editionId })
        val india = tempRepository.load(EditionIds.INDIA)
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)
    }

    private fun assertCatalogError(catalogJson: String, expectedFragment: String) {
        val tempRoot = Files.createTempDirectory("edition-catalog-error").resolve("data")
        copyDataTree(tempRoot)
        tempRoot.resolve("editions/index.json").writeText(catalogJson)
        val tempRepository = EditionRepository(FileEditionFileSource(tempRoot))
        val error = runCatching { tempRepository.loadEditionCatalog() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException || error is IllegalStateException)
        assertTrue(error!!.message!!.contains(expectedFragment, ignoreCase = true))
    }

    private fun copyDataTree(targetRoot: java.nio.file.Path) {
        Files.createDirectories(targetRoot.resolve("common"))
        Files.createDirectories(targetRoot.resolve("editions/uk"))
        Files.createDirectories(targetRoot.resolve("editions/india"))
        TestFixtures.dataDir.resolve("common").toFile().listFiles()?.forEach { file ->
            Files.copy(file.toPath(), targetRoot.resolve("common/${file.name}"))
        }
        listOf("uk", "india").forEach { editionId ->
            TestFixtures.dataDir.resolve("editions/$editionId").toFile().listFiles()?.forEach { file ->
                Files.copy(file.toPath(), targetRoot.resolve("editions/$editionId/${file.name}"))
            }
        }
        Files.copy(
            TestFixtures.dataDir.resolve("editions/index.json"),
            targetRoot.resolve("editions/index.json"),
        )
    }
}
