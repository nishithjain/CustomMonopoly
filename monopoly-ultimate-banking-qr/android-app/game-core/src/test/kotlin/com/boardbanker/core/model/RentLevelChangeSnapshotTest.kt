package com.boardbanker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RentLevelChangeSnapshotTest {
    @Test
    fun storesAndReadsOldAndNewLevels() {
        val transaction = Transaction(
            transactionId = "TX_1",
            gameId = "GAME_1",
            timestamp = 1L,
            transactionType = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            propertyId = "PRP_15",
            playerId = "USR_01",
            amount = 4,
            stateBefore = RentLevelChangeSnapshot.stateBefore(3),
            stateAfter = RentLevelChangeSnapshot.stateAfter(4),
        )

        assertEquals(3, RentLevelChangeSnapshot.oldLevel(transaction))
        assertEquals(4, RentLevelChangeSnapshot.newLevel(transaction))
        assertEquals("From L3 → L4", RentLevelChangeSnapshot.levelChangeText(3, 4))
    }

    @Test
    fun decreaseUsesActualOldAndNewLevels() {
        assertEquals("From L4 → L3", RentLevelChangeSnapshot.levelChangeText(4, 3))
    }

    @Test
    fun missingOldLevelFallsBackToToLevelText() {
        val transaction = Transaction(
            transactionId = "TX_2",
            gameId = "GAME_1",
            timestamp = 2L,
            transactionType = TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
            propertyId = "PRP_15",
            amount = 4,
        )

        assertNull(RentLevelChangeSnapshot.oldLevel(transaction))
        assertEquals(4, RentLevelChangeSnapshot.newLevel(transaction))
        assertEquals("To L4", RentLevelChangeSnapshot.levelChangeText(null, 4))
    }
}
