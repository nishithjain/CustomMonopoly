package com.boardbanker.app.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerActiveEventDisplayTest {
    @Test
    fun singleEventUsesSingularLabel() {
        assertEquals(
            listOf("Event applied: Rent Relief"),
            PlayerActiveEventDisplay.formatLines(listOf("Rent Relief")),
        )
    }

    @Test
    fun multipleEventsUseBulletList() {
        assertEquals(
            listOf(
                "Events applied:",
                "• Rent Relief",
                "• Second Wind",
            ),
            PlayerActiveEventDisplay.formatLines(listOf("Rent Relief", "Second Wind")),
        )
    }
}
