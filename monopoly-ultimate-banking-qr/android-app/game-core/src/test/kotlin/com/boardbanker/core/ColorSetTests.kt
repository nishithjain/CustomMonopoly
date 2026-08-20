package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSetTests {
    private val engine = TestFixtures.engine

    @Test
    fun tsColorset001_singleOwnerCompletionBonus() {
        var session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", 2)
        val result = engine.process(
            session,
            GameCommand.PurchaseProperty("USR_01", "PRP_02"),
        )
        assertEquals(4, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertEquals(3, result.session.properties["PRP_02"]!!.currentRentLevel)
        assertTrue(result.session.colorGroups["BROWN"]!!.completionBonusApplied)
    }

    @Test
    fun tsColorset002_multiOwnerCompletionBonus() {
        var session = TestFixtures.newGame(listOf("USR_01", "USR_02"))
        session = session.copy(
            properties = session.properties.mapValues { (id, state) ->
                when (id) {
                    "PRP_06" -> state.copy(ownerPlayerId = "USR_01", currentRentLevel = 2)
                    "PRP_07" -> state.copy(ownerPlayerId = "USR_02", currentRentLevel = 2)
                    else -> state
                }
            },
        )
        val result = engine.process(
            session,
            GameCommand.PurchaseProperty("USR_02", "PRP_08"),
        )
        assertEquals(3, result.session.properties["PRP_06"]!!.currentRentLevel)
        assertEquals(3, result.session.properties["PRP_07"]!!.currentRentLevel)
        assertEquals(2, result.session.properties["PRP_08"]!!.currentRentLevel)
        assertTrue(result.session.colorGroups["PINK"]!!.completionBonusApplied)
    }
}
