package com.boardbanker.app.player

import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.playerDisplayIconDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerIconRegistryTest {
    @Test
    fun usr01MapsToCarIcon() {
        assertEquals("player_car", PlayerIconRegistry.runtimeResourceName("USR_01"))
    }

    @Test
    fun usr02MapsToHelicopterIcon() {
        assertEquals("player_helicopter", PlayerIconRegistry.runtimeResourceName("USR_02"))
    }

    @Test
    fun usr03MapsToShipIcon() {
        assertEquals("player_ship", PlayerIconRegistry.runtimeResourceName("USR_03"))
    }

    @Test
    fun usr04MapsToAeroplaneIcon() {
        assertEquals("player_aeroplane", PlayerIconRegistry.runtimeResourceName("USR_04"))
    }

    @Test
    fun unknownPlayerReturnsNullResourceName() {
        assertNull(PlayerIconRegistry.runtimeResourceName("USR_99"))
        assertNull(PlayerIconRegistry.runtimeResourceName(null))
    }

    @Test
    fun iconLabelMapsKnownPlayers() {
        assertEquals("Car", PlayerIconRegistry.iconLabel("USR_01"))
        assertEquals("Aeroplane", PlayerIconRegistry.iconLabel("USR_04"))
        assertEquals("Player", PlayerIconRegistry.iconLabel(null))
    }

    @Test
    fun fallbackIconUsedWhenPlayerIdMissing() {
        assertNotEquals(
            PlayerIconRegistry.iconResId("USR_01"),
            PlayerIconRegistry.iconResIdOrFallback(null),
        )
        assertEquals(
            com.boardbanker.app.R.drawable.ic_player_fallback,
            PlayerIconRegistry.iconResIdOrFallback(null),
        )
        assertEquals(
            com.boardbanker.app.R.drawable.ic_player_fallback,
            PlayerIconRegistry.iconResIdOrFallback("USR_99"),
        )
    }

    @Test
    fun accessibilityDescriptionIncludesIconAndPlayerName() {
        assertEquals(
            "Car, Player Nishith",
            playerDisplayIconDescription("USR_01", "Nishith"),
        )
        assertEquals(
            "Aeroplane, Player Aditya",
            playerDisplayIconDescription("USR_04", "Aditya", PlayerIconSize.Small),
        )
    }
}
