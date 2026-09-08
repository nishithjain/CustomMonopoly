package com.boardbanker.app.ui.screens.playerdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDetailsActionAvailabilityTest {
    @Test
    fun jailedPlayer_disablesCollectGoAndLocation() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = true,
            activePlayerName = "Nishith",
            inJail = true,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertTrue(availability.getOutOfJailEnabled)
        assertNull(availability.actionsDisabledReason)
    }

    @Test
    fun nonJailedActivePlayer_enablesNormalActions() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = true,
            activePlayerName = "Nishith",
            inJail = false,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertTrue(availability.collectGoEnabled)
        assertTrue(availability.locationEnabled)
        assertTrue(availability.goToJailEnabled)
        assertFalse(availability.getOutOfJailEnabled)
    }

    @Test
    fun nonActivePlayer_disablesAllActionsWithReason() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = false,
            activePlayerName = "Nishith",
            inJail = false,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertFalse(availability.getOutOfJailEnabled)
        assertEquals(
            "Actions are disabled because it is Nishith's turn.",
            availability.actionsDisabledReason,
        )
    }

    @Test
    fun nonActiveJailedPlayer_disablesGetOutOfJail() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = false,
            activePlayerName = "Aditya",
            inJail = true,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertFalse(availability.getOutOfJailEnabled)
        assertEquals(
            "Actions are disabled because it is Aditya's turn.",
            availability.actionsDisabledReason,
        )
    }

    @Test
    fun commandInFlight_disablesAllActions() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            isCurrentPlayer = true,
            activePlayerName = "Nishith",
            inJail = false,
            commandInFlight = true,
            step = PlayerDetailsStep.Hub,
        )
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertFalse(availability.getOutOfJailEnabled)
    }
}
