package com.boardbanker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JailStatusSnapshotTest {
    @Test
    fun enteredJailDetectsTransitionToJail() {
        val tx = Transaction(
            transactionId = "G_TX_1",
            gameId = "G_1",
            timestamp = 1_000L,
            transactionType = TransactionType.JAIL_STATUS_CHANGE,
            playerId = "USR_01",
            stateBefore = JailStatusSnapshot.stateBefore(false),
            stateAfter = JailStatusSnapshot.stateAfter(true),
        )

        assertTrue(JailStatusSnapshot.enteredJail(tx))
        assertFalse(JailStatusSnapshot.releasedFromJail(tx))
    }

    @Test
    fun releasedFromJailDetectsTransitionOutOfJail() {
        val tx = Transaction(
            transactionId = "G_TX_2",
            gameId = "G_1",
            timestamp = 2_000L,
            transactionType = TransactionType.JAIL_STATUS_CHANGE,
            playerId = "USR_01",
            stateBefore = JailStatusSnapshot.stateBefore(true),
            stateAfter = JailStatusSnapshot.stateAfter(false),
        )

        assertFalse(JailStatusSnapshot.enteredJail(tx))
        assertTrue(JailStatusSnapshot.releasedFromJail(tx))
    }

    @Test
    fun legacyJailStatusWithoutSnapshotsReturnsFalse() {
        val tx = Transaction(
            transactionId = "G_TX_3",
            gameId = "G_1",
            timestamp = 3_000L,
            transactionType = TransactionType.JAIL_STATUS_CHANGE,
            playerId = "USR_01",
        )

        assertFalse(JailStatusSnapshot.enteredJail(tx))
        assertFalse(JailStatusSnapshot.releasedFromJail(tx))
    }
}
