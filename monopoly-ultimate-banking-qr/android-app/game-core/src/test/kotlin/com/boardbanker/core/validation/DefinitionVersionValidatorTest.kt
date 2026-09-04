package com.boardbanker.core.validation

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.model.CurrencyDefinition
import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionVersionValidatorTest {
    @Test
    fun ukEditionLoadsWithVersionOne() {
        assertEquals(1, TestFixtures.definitions.edition!!.definitionVersion)
    }

    @Test
    fun indiaEditionLoadsWithVersionThree() {
        assertEquals(3, TestFixtures.loadEdition(EditionIds.INDIA).edition!!.definitionVersion)
    }

    @Test
    fun zeroVersionFailsValidation() {
        val problems = DefinitionVersionValidator.validate(sampleEdition(definitionVersion = 0))
        assertTrue(problems.any { it.contains("definitionVersion must be >= 1") })
    }

    @Test
    fun negativeVersionFailsValidation() {
        val problems = DefinitionVersionValidator.validate(sampleEdition(definitionVersion = -1))
        assertTrue(problems.any { it.contains("definitionVersion must be >= 1") })
    }

    private fun sampleEdition(definitionVersion: Int): EditionDefinition =
        EditionDefinition(
            editionId = "sample",
            definitionVersion = definitionVersion,
            name = "Sample",
            countryCode = "GB",
            currency = CurrencyDefinition(code = "M", symbol = "M", scale = 1),
        )
}
