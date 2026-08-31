package com.boardbanker.core.validation

import com.boardbanker.core.TestEditionResources
import com.boardbanker.core.TestFixtures
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.CardConfiguration
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionValidatorTest {
    private val validator = DefinitionValidator()

    @Test
    fun ukEditionUsesConfiguredCounts() {
        val definitions = TestFixtures.definitions
        val config = definitions.edition!!.cardConfiguration!!

        assertEquals(4, config.playerCardCount)
        assertEquals(22, config.propertyCardCount)
        assertEquals(23, config.eventCardCount)
        assertEquals(5, config.rentLevelsPerProperty)
        assertEquals(49, CardConfigurationValidator.expectedTotalCards(config))
        assertEquals(4, definitions.players.size)
        assertEquals(22, definitions.properties.size)
        assertEquals(23, definitions.events.size)
        assertEquals(49, definitions.cards.size)
        assertEquals(40, definitions.boardLayout.size)
        assertTrue(validator.isValid(definitions))
    }

    @Test
    fun indiaEditionUsesConfiguredCounts() {
        val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
        val config = definitions.edition!!.cardConfiguration!!

        assertEquals(4, config.playerCardCount)
        assertEquals(22, config.propertyCardCount)
        assertEquals(23, config.eventCardCount)
        assertEquals(5, config.rentLevelsPerProperty)
        assertTrue(validator.isValid(definitions))
    }

    @Test
    fun customTestEditionValidatesWithoutKotlinConstants() {
        val repository = EditionRepository(FileEditionFileSource(TestEditionResources.customTestDataDir()))
        val definitions = repository.load(TestEditionResources.CUSTOM_TEST_EDITION_ID)
        val config = definitions.edition!!.cardConfiguration!!

        assertEquals(5, config.playerCardCount)
        assertEquals(24, config.propertyCardCount)
        assertEquals(20, config.eventCardCount)
        assertEquals(6, config.rentLevelsPerProperty)
        assertEquals(49, CardConfigurationValidator.expectedTotalCards(config))
        assertEquals(32, definitions.boardLayout.size)
        assertEquals(24, definitions.boardLayout.propertySpaces.size)
        assertEquals(4, definitions.boardLayout.eventSpaces.size)
        assertTrue(definitions.properties.containsKey("CTP_01"))
        assertTrue(definitions.events.containsKey("CEV_01"))
        assertTrue(validator.isValid(definitions))
    }

    @Test
    fun missingCardConfigurationFailsValidation() {
        val edition = TestFixtures.definitions.edition!!.copy(cardConfiguration = null)
        val problems = CardConfigurationValidator.validate(edition)
        assertTrue(problems.any { it.contains("cardConfiguration is missing") })
    }

    @Test
    fun wrongPropertyCountReportsExpectedAndActual() {
        val definitions = TestFixtures.definitions.copy(
            properties = TestFixtures.definitions.properties.filterKeys { it != "PRP_22" },
        )
        val problems = validator.validate(definitions)
        assertTrue(
            problems.any {
                it.contains("Edition 'uk'") &&
                    it.contains("Property Cards") &&
                    it.contains("expected 22") &&
                    it.contains("found 21")
            },
        )
    }

    @Test
    fun wrongRentLevelCountIncludesPropertyId() {
        val property = TestFixtures.definitions.properties["PRP_01"]!!
        val definitions = TestFixtures.definitions.copy(
            properties = TestFixtures.definitions.properties + (
                "PRP_01" to property.copy(rentLevels = property.rentLevels.dropLast(1))
            ),
        )
        val problems = validator.validate(definitions)
        assertTrue(
            problems.any {
                it.contains("Edition 'uk', property 'PRP_01'") &&
                    it.contains("expected 5 rent levels") &&
                    it.contains("found 4")
            },
        )
    }

    @Test
    fun wrongTotalCardCountIsDerivedFromConfiguration() {
        val definitions = TestFixtures.definitions.copy(
            cards = TestFixtures.definitions.cards.filterKeys { it != "EVT_23" },
            cardsByQrPayload = TestFixtures.definitions.cardsByQrPayload.filterKeys {
                TestFixtures.definitions.cards["EVT_23"]?.qrPayload != it
            },
        )
        val problems = validator.validate(definitions)
        assertTrue(
            problems.any {
                it.contains("Edition 'uk'") &&
                    it.contains("expected 49 total cards") &&
                    it.contains("found 48")
            },
        )
    }

    @Test
    fun duplicateBoardPositionsFailValidation() {
        val layout = TestFixtures.definitions.boardLayout.copy(
            spaces = TestFixtures.definitions.boardLayout.spaces.mapIndexed { index, space ->
                if (index == 5) space.copy(position = 1) else space
            },
        )
        val problems = validator.validate(TestFixtures.definitions.copy(boardLayout = layout))
        assertTrue(problems.any { it.contains("duplicate positions") })
    }

    @Test
    fun unknownPropertyBoardTargetFailsValidation() {
        val layout = TestFixtures.definitions.boardLayout.copy(
            spaces = TestFixtures.definitions.boardLayout.spaces.map { space ->
                if (space.spaceId == "PROPERTY_PRP_01_SPACE") {
                    space.copy(targetId = "PRP_MISSING")
                } else {
                    space
                }
            },
        )
        val problems = validator.validate(TestFixtures.definitions.copy(boardLayout = layout))
        assertTrue(problems.any { it.contains("unknown property target 'PRP_MISSING'") })
    }

    private fun loadCustomTestEdition() = TestEditionResources.loadCustomTestEdition()
}
