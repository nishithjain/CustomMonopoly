package com.boardbanker.app.ui.components

/**
 * Label helpers for hardware-inspired banking action semantics.
 *
 * Symbols provide familiarity with the physical Ultimate Banking unit;
 * trailing text makes the Android action explicit.
 */
object BankingActionLabels {
    const val CONFIRM_SYMBOL = "✓"
    const val MIDDLE_SYMBOL = "M"
    const val CANCEL_SYMBOL = "✕"

    fun confirm(action: String): String = "$CONFIRM_SYMBOL $action"

    fun middle(action: String): String = "$MIDDLE_SYMBOL $action"

    fun cancel(action: String = "CANCEL"): String = "$CANCEL_SYMBOL $action"

    fun hasMiddleAction(middleLabel: String?, onMiddle: (() -> Unit)?): Boolean =
        !middleLabel.isNullOrBlank() && onMiddle != null

    fun hasConfirmAction(confirmLabel: String?, onConfirm: (() -> Unit)?): Boolean =
        !confirmLabel.isNullOrBlank() && onConfirm != null

    fun hasCancelAction(cancelLabel: String?, onCancel: (() -> Unit)?): Boolean =
        !cancelLabel.isNullOrBlank() && onCancel != null

    fun useHorizontalThreeButtonLayout(
        confirmLabel: String?,
        onConfirm: (() -> Unit)?,
        middleLabel: String?,
        onMiddle: (() -> Unit)?,
        cancelLabel: String?,
        onCancel: (() -> Unit)?,
    ): Boolean = hasConfirmAction(confirmLabel, onConfirm) &&
        hasMiddleAction(middleLabel, onMiddle) &&
        hasCancelAction(cancelLabel, onCancel)
}
