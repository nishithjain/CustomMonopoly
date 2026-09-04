package com.boardbanker.app.ui.screens.playerdetails

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDetailsActionAvailabilityTest {
    @Test
    fun jailedPlayer_disablesCollectGoAndLocation() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
            inJail = true,
            commandInFlight = false,
            step = PlayerDetailsStep.Hub,
        )
        assertFalse(availability.collectGoEnabled)
        assertFalse(availability.locationEnabled)
        assertFalse(availability.goToJailEnabled)
        assertTrue(availability.getOutOfJailEnabled)
    }

    @Test
    fun nonJailedPlayer_enablesNormalActions() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
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
    fun commandInFlight_disablesAllActions() {
        val availability = PlayerDetailsActionAvailability.forPlayer(
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
