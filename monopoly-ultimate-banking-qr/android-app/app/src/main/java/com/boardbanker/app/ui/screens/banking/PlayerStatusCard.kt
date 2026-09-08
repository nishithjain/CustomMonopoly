package com.boardbanker.app.ui.screens.banking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.CommonUiIconImage
import com.boardbanker.app.ui.components.IconLabelRow
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerStatusCard(
    player: PlayerStatusUi,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (player.isCurrentTurn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (player.isCurrentTurn) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardDescription = buildString {
        append(player.playerName)
        append(", balance ")
        append(player.balanceText)
        append(", ")
        append(player.propertyCountLabel)
        if (player.energyGridCountLabel != null) {
            append(", ")
            append(player.energyGridCountLabel)
        }
        append(", status ")
        append(player.statusLabel)
        if (player.isCurrentTurn) append(", current turn")
        if (player.effectBadges.isNotEmpty()) {
            append(", active effects ")
            append(player.effectBadges.joinToString())
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(GameStatusTestTags.playerCard(player.playerId))
            .semantics { contentDescription = cardDescription },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = if (player.isCurrentTurn) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIdentity(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    iconSize = PlayerIconSize.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (player.isCurrentTurn) {
                    CurrentTurnBadge(
                        modifier = Modifier.testTag(GameStatusTestTags.currentTurnBadge(player.playerId)),
                    )
                }
            }

            PlayerStatusStatRow(
                leftLabel = "Balance",
                leftValue = player.balanceText,
                leftValueTestTag = GameStatusTestTags.balance(player.playerId),
                rightLabel = "Properties",
                rightValue = player.propertyCountLabel,
                rightValueTestTag = GameStatusTestTags.properties(player.playerId),
                rightIcon = CommonUiIcon.PROPERTY,
            )

            if (player.hasEnergyGridsInEdition && player.energyGridCountLabel != null) {
                PlayerStatusStatRow(
                    leftLabel = "Energy Grids",
                    leftValue = player.energyGridCountLabel,
                    leftValueTestTag = GameStatusTestTags.energyGrids(player.playerId),
                    leftIcon = CommonUiIcon.ENERGY_GRID,
                    rightLabel = "Status",
                    rightValue = player.statusLabel,
                    rightValueTestTag = GameStatusTestTags.status(player.playerId),
                    rightIcon = player.statusIcon,
                )
            } else {
                PlayerStatusStatRow(
                    leftLabel = "Status",
                    leftValue = player.statusLabel,
                    leftValueTestTag = GameStatusTestTags.status(player.playerId),
                    leftIcon = player.statusIcon,
                    rightLabel = null,
                    rightValue = null,
                    rightValueTestTag = null,
                    rightIcon = null,
                )
            }

            if (player.effectBadges.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    player.effectBadges.forEach { badge ->
                        EffectBadge(label = badge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTurnBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        IconLabelRow(
            icon = CommonUiIcon.CURRENT_TURN,
            label = "Current turn",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            iconSize = 18.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlayerStatusStatRow(
    leftLabel: String,
    leftValue: String,
    leftValueTestTag: String,
    leftIcon: CommonUiIcon? = null,
    rightLabel: String?,
    rightValue: String?,
    rightValueTestTag: String?,
    rightIcon: CommonUiIcon? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerStatusStatCell(
            label = leftLabel,
            value = leftValue,
            valueTestTag = leftValueTestTag,
            icon = leftIcon,
            modifier = Modifier.weight(1f),
            emphasizeValue = leftLabel == "Balance",
        )
        if (rightLabel != null && rightValue != null && rightValueTestTag != null) {
            PlayerStatusStatCell(
                label = rightLabel,
                value = rightValue,
                valueTestTag = rightValueTestTag,
                icon = rightIcon,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerStatusStatCell(
    label: String,
    value: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
    icon: CommonUiIcon? = null,
    emphasizeValue: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconLabelRow(
            icon = icon,
            label = label,
            iconSize = 16.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = if (emphasizeValue) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(valueTestTag),
        )
    }
}

@Composable
private fun EffectBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
    ) {
        IconLabelRow(
            icon = CommonUiIcon.EVENT_CARD,
            label = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            iconSize = 16.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
    }
}
