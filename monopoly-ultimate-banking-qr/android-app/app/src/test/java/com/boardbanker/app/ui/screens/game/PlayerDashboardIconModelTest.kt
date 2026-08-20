package com.boardbanker.app.ui.screens.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDashboardIconModelTest {
    @Test
    fun dashboardUsesCustomPlayerNames() {
        val players = listOf(
            PlayerDashboardUi(playerId = "USR_01", playerName = "Nishith", balanceText = "M1500"),
            PlayerDashboardUi(playerId = "USR_02", playerName = "Aditya", balanceText = "M1500"),
        )
        assertEquals("USR_01", players[0].playerId)
        assertEquals("Nishith", players[0].playerName)
        assertEquals("USR_02", players[1].playerId)
        assertEquals("Aditya", players[1].playerName)
    }
}
