package com.boardbanker.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
