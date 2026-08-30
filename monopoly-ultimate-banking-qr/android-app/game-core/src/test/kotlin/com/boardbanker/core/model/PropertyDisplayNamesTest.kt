package com.boardbanker.core.model

import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PropertyDisplayNamesTest {
    private val uk = TestFixtures.definitions
    private val india = TestFixtures.loadEdition(EditionIds.INDIA)

    @Test
    fun propertyOneDisplaysWithNumberPrefix() {
        val property = uk.properties["PRP_01"]!!
        assertEquals("[1] Old Kent Road", property.displayNameWithNumber())
        assertEquals("Old Kent Road", property.name)
    }

    @Test
    fun propertyFifteenDisplaysWithNumberPrefix() {
        val property = uk.properties["PRP_15"]!!
        assertEquals("[15] Leicester Square", property.displayNameWithNumber())
        assertEquals("Leicester Square", property.name)
    }

    @Test
    fun propertyTwentyTwoDisplaysWithNumberPrefix() {
        val property = uk.properties["PRP_22"]!!
        assertEquals("[22] Mayfair", property.displayNameWithNumber())
    }

    @Test
    fun numbersAreNotZeroPadded() {
        val property = uk.properties["PRP_01"]!!
        assertEquals("[1] Old Kent Road", property.displayNameWithNumber())
        assertNotEquals("[01] Old Kent Road", property.displayNameWithNumber())
    }

    @Test
    fun formatterWorksForEveryEdition() {
        val ukName = PropertyDisplayNames.displayNameWithNumber("PRP_01", uk)
        val indiaName = PropertyDisplayNames.displayNameWithNumber("PRP_01", india)
        assertEquals("[1] Old Kent Road", ukName)
        assertEquals("[1] Cubbon Park", indiaName)
        assertEquals("Old Kent Road", uk.properties["PRP_01"]!!.name)
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)
    }

    @Test
    fun displayNameWithNumberDoesNotDuplicatePrefix() {
        val property = uk.properties["PRP_01"]!!
        val formatted = property.displayNameWithNumber()
        assertEquals(formatted, PropertyDisplayNames.displayNameWithNumber(property))
        assertEquals(formatted, PropertyDisplayNames.displayNameWithNumber("PRP_01", uk))
    }

    @Test
    fun propertyNumberExtractsFromPropertyId() {
        assertEquals(1, PropertyDisplayNames.propertyNumber("PRP_01"))
        assertEquals(15, PropertyDisplayNames.propertyNumber("PRP_15"))
        assertEquals(22, PropertyDisplayNames.propertyNumber("PRP_22"))
    }
}
