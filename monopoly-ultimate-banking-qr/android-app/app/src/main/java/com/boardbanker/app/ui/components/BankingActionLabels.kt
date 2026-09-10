package com.boardbanker.app.ui.components

/**
 * Label helpers for banking action buttons.
 *
 * Icons are rendered separately via [IconLabelRow]; labels are plain action text.
 */
object BankingActionLabels {
    fun confirm(action: String): String = action

    fun middle(action: String): String = action

    fun cancel(action: String = "CANCEL"): String = action

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
