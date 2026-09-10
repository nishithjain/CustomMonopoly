package com.boardbanker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonUiIcon

data class BankingExtraAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val icon: CommonUiIcon? = null,
    val testTag: String? = null,
)

/**
 * Contextual confirm / middle / cancel actions inspired by the physical banking unit.
 *
 * Only renders actions that are provided. The middle M action is omitted when absent.
 * Does not perform validation or audio feedback — callers invoke existing ViewModel actions.
 */
@Composable
fun BankingActionBar(
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmIcon: CommonUiIcon = CommonUiIcon.CHECK,
    confirmTestTag: String? = null,
    middleLabel: String? = null,
    onMiddle: (() -> Unit)? = null,
    middleEnabled: Boolean = true,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    cancelEnabled: Boolean = true,
    cancelTestTag: String? = null,
    extraActions: List<BankingExtraAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val showConfirm = BankingActionLabels.hasConfirmAction(confirmLabel, onConfirm)
    val showMiddle = BankingActionLabels.hasMiddleAction(middleLabel, onMiddle)
    val showCancel = BankingActionLabels.hasCancelAction(cancelLabel, onCancel)
    val useHorizontal = BankingActionLabels.useHorizontalThreeButtonLayout(
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        middleLabel = middleLabel,
        onMiddle = onMiddle,
        cancelLabel = cancelLabel,
        onCancel = onCancel,
    )

    if (!showConfirm && !showMiddle && !showCancel && extraActions.isEmpty()) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (useHorizontal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BankingConfirmButton(
                    label = confirmLabel!!,
                    onClick = onConfirm!!,
                    enabled = confirmEnabled,
                    icon = confirmIcon,
                    testTag = confirmTestTag,
                    modifier = Modifier.weight(1f),
                )
                BankingMiddleButton(
                    label = middleLabel!!,
                    onClick = onMiddle!!,
                    enabled = middleEnabled,
                    modifier = Modifier.weight(1f),
                )
                BankingCancelButton(
                    label = cancelLabel!!,
                    onClick = onCancel!!,
                    enabled = cancelEnabled,
                    testTag = cancelTestTag,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            if (showConfirm) {
                BankingConfirmButton(
                    label = confirmLabel!!,
                    onClick = onConfirm!!,
                    enabled = confirmEnabled,
                    icon = confirmIcon,
                    testTag = confirmTestTag,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            extraActions.forEach { action ->
                Button(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier)
                        .semantics { contentDescription = action.contentDescription },
                ) {
                    IconLabelRow(
                        icon = action.icon,
                        label = action.label,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (showMiddle) {
                BankingMiddleButton(
                    label = middleLabel!!,
                    onClick = onMiddle!!,
                    enabled = middleEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showCancel) {
                BankingCancelButton(
                    label = cancelLabel!!,
                    onClick = onCancel!!,
                    enabled = cancelEnabled,
                    testTag = cancelTestTag,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BankingConfirmButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: CommonUiIcon,
    testTag: String? = null,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { contentDescription = accessibilityLabel(label, "Confirm") },
    ) {
        IconLabelRow(
            icon = icon,
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BankingMiddleButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = accessibilityLabel(label, "Middle action") },
    ) {
        IconLabelRow(
            icon = CommonUiIcon.COLLECT,
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BankingCancelButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { contentDescription = accessibilityLabel(label, "Cancel") },
    ) {
        IconLabelRow(
            icon = cancelIconForLabel(label),
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private fun accessibilityLabel(label: String, fallback: String): String {
    val trimmed = label.trim()
    return if (trimmed.isNotEmpty()) trimmed else fallback
}
