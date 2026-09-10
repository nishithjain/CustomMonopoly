package com.boardbanker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.core.model.EntityRef

@Composable
fun GameplayResultPresentation(
    result: GameplayResultUiModel,
    modifier: Modifier = Modifier,
    highlightIconSize: PlayerIconSize = PlayerIconSize.Normal,
    showLargePrimaryPlayer: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (result.title == "RENT WAIVED") {
                    contentDescription = "Rent waived. ${result.primaryMessage}"
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(result.title, style = MaterialTheme.typography.headlineSmall)

        val primarySize = if (showLargePrimaryPlayer) PlayerIconSize.Large else highlightIconSize
        val primaryName = result.primaryPlayerName
        val secondaryName = result.secondaryPlayerName

        if (result.primaryPlayerId != null && result.secondaryPlayerId != null && primaryName != null && secondaryName != null) {
            val toIdentity = when (result.secondaryPlayerId) {
                EntityRef.BANK -> DisplayIdentity.Bank
                EntityRef.JAIL -> DisplayIdentity.Jail
                else -> DisplayIdentity.Player(result.secondaryPlayerId, secondaryName)
            }
            val fromIdentity = when (result.primaryPlayerId) {
                EntityRef.BANK -> DisplayIdentity.Bank
                else -> DisplayIdentity.Player(result.primaryPlayerId, primaryName)
            }
            PlayerTransferRow(
                fromPlayerId = result.primaryPlayerId,
                fromPlayerName = primaryName,
                toPlayerId = when (result.secondaryPlayerId) {
                    EntityRef.BANK, EntityRef.JAIL -> null
                    else -> result.secondaryPlayerId
                },
                toPlayerName = secondaryName,
                iconSize = highlightIconSize,
                fromIdentity = fromIdentity,
                toIdentity = toIdentity,
            )
        } else if (result.primaryPlayerId != null && primaryName != null) {
            PlayerIdentity(
                playerId = result.primaryPlayerId,
                playerName = primaryName,
                iconSize = primarySize,
                vertical = showLargePrimaryPlayer,
            )
        }

        if (result.playerRankings.isNotEmpty()) {
            result.playerRankings.forEachIndexed { index, ranking ->
                PlayerRankingRow(index = index + 1, ranking = ranking)
            }
        }

        if (result.primaryMessage.isNotBlank()) {
            Text(result.primaryMessage, style = MaterialTheme.typography.bodyLarge)
        }

        val nextTurnPlayerId = result.nextTurnPlayerId
        val nextTurnPlayerName = result.nextTurnPlayerName
        if (nextTurnPlayerId != null && nextTurnPlayerName != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Next turn:", style = MaterialTheme.typography.bodyLarge)
                CommonUiIconImage(
                    icon = CommonUiIcon.CURRENT_TURN,
                    size = highlightIconSize.size,
                    contentDescription = "Current turn",
                )
                PlayerIdentity(
                    playerId = nextTurnPlayerId,
                    playerName = nextTurnPlayerName,
                    iconSize = highlightIconSize,
                )
            }
        }

        result.balanceChanges.forEach { change ->
            if (change.playerId != null &&
                change.playerId != result.primaryPlayerId &&
                change.playerId != result.secondaryPlayerId
            ) {
                PlayerIdentity(
                    playerId = change.playerId,
                    playerName = change.playerName,
                    iconSize = PlayerIconSize.Small,
                )
            }
        }

        result.propertyChanges.forEach { change ->
            if (change.ownerPlayerId != null && change.ownerName != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = change.propertyName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Property ${change.propertyName}"
                        },
                    )
                    Text("Owner:", style = MaterialTheme.typography.labelMedium)
                    PlayerIdentity(
                        playerId = change.ownerPlayerId,
                        playerName = change.ownerName,
                        iconSize = PlayerIconSize.Small,
                    )
                }
            }
        }

        result.temporaryEffectMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        result.lastTransactionSummary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        result.physicalInstructions.forEach { instruction ->
            Text(instruction, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlayerRankingRow(
    index: Int,
    ranking: com.boardbanker.app.gameplay.presentation.PlayerRankingUi,
) {
    val label = if (ranking.bankrupt) "BANKRUPT" else ranking.wealthText
    RowWithRanking(
        prefix = "$index.",
        playerId = ranking.playerId,
        playerName = ranking.playerName,
        suffix = label,
    )
}

@Composable
private fun RowWithRanking(
    prefix: String,
    playerId: String,
    playerName: String,
    suffix: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(prefix, style = MaterialTheme.typography.bodyMedium)
            PlayerIdentity(
                playerId = playerId,
                playerName = playerName,
                iconSize = PlayerIconSize.Small,
            )
        }
        Text(suffix, style = MaterialTheme.typography.bodyMedium)
    }
}
