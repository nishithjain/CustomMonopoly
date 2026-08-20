package com.boardbanker.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingActionLabelsTest {
    @Test
    fun confirmLabelIncludesSymbolAndText() {
        assertEquals("✓ START GAME", BankingActionLabels.confirm("START GAME"))
        assertEquals("✓ COLLECT M200", BankingActionLabels.confirm("COLLECT M200"))
    }

    @Test
    fun middleLabelIncludesSymbolAndText() {
        assertEquals("M BID +M20", BankingActionLabels.middle("BID +M20"))
    }

    @Test
    fun cancelLabelDefaultsToCancel() {
        assertEquals("✕ CANCEL", BankingActionLabels.cancel())
        assertEquals("✕ BACK", BankingActionLabels.cancel("BACK"))
    }

    @Test
    fun middleActionHiddenWhenAbsent() {
        assertFalse(BankingActionLabels.hasMiddleAction(null, {}))
        assertFalse(BankingActionLabels.hasMiddleAction("M BID", null))
        assertTrue(BankingActionLabels.hasMiddleAction("M BID", {}))
    }

    @Test
    fun horizontalLayoutRequiresAllThreeActions() {
        assertTrue(
            BankingActionLabels.useHorizontalThreeButtonLayout(
                confirmLabel = "✓ CONFIRM",
                onConfirm = {},
                middleLabel = "M BID",
                onMiddle = {},
                cancelLabel = "✕ CANCEL",
                onCancel = {},
            ),
        )
        assertFalse(
            BankingActionLabels.useHorizontalThreeButtonLayout(
                confirmLabel = "✓ CONFIRM",
                onConfirm = {},
                middleLabel = null,
                onMiddle = null,
                cancelLabel = "✕ CANCEL",
                onCancel = {},
            ),
        )
    }
}
