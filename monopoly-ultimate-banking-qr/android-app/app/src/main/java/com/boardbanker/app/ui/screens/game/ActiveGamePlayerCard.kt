package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity

@Composable
fun ActiveGamePlayerCard(
    player: PlayerDashboardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (player.isActiveTurn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (player.isActiveTurn) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("active_game_player_card_${player.playerId}")
            .semantics {
                contentDescription = "Player ${player.playerName}"
            },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (player.isActiveTurn) 2.dp else 1.dp,
            color = borderColor,
        ),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                PlayerIdentity(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    iconSize = PlayerIconSize.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (player.isActiveTurn) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Current Turn",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Text(
                text = player.balanceText,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = player.assetsSummaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = player.statusText,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    player.isBankrupt -> MaterialTheme.colorScheme.error
                    player.inJail -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )

            player.activeEventLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
