package com.boardbanker.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayLabelFormattersTest {
    @Test
    fun formatEnumLabelConvertsSnakeCase() {
        assertEquals("Light Blue", formatEnumLabel("LIGHT_BLUE"))
        assertEquals("Renewable Energy Grid", formatEnumLabel("RENEWABLE_ENERGY_GRID"))
    }

    @Test
    fun pluralizeUsesSingularAndPluralForms() {
        assertEquals("Property", pluralize(1, "Property"))
        assertEquals("Energy Grids", pluralize(2, "Energy Grid"))
    }
}
