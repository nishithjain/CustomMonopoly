package com.boardbanker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.PlayerIconRegistry

enum class PlayerIconSize(val size: Dp) {
    Compact(22.dp),
    Small(24.dp),
    Normal(36.dp),
    Large(80.dp),
}

@Composable
fun PlayerIdentity(
    playerId: String?,
    playerName: String,
    modifier: Modifier = Modifier,
    iconSize: PlayerIconSize = PlayerIconSize.Normal,
    vertical: Boolean = false,
    showFallbackIcon: Boolean = false,
) {
    val iconResId = if (showFallbackIcon) {
        PlayerIconRegistry.iconResIdOrFallback(playerId)
    } else {
        PlayerIconRegistry.iconResId(playerId)
    }
    val textStyle = if (vertical) {
        MaterialTheme.typography.titleMedium
    } else if (iconSize == PlayerIconSize.Small) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (iconResId != null) {
                PlayerIconImage(
                    iconResId = iconResId,
                    playerId = playerId,
                    playerName = playerName,
                    size = iconSize,
                )
            }
            Text(playerName, style = textStyle)
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (iconResId != null) {
                PlayerIconImage(
                    iconResId = iconResId,
                    playerId = playerId,
                    playerName = playerName,
                    size = iconSize,
                )
            }
            Text(playerName, style = textStyle)
        }
    }
}

@Composable
fun PlayerTransferRow(
    fromPlayerId: String?,
    fromPlayerName: String,
    toPlayerId: String?,
    toPlayerName: String,
    modifier: Modifier = Modifier,
    iconSize: PlayerIconSize = PlayerIconSize.Normal,
    amount: String? = null,
    fromIdentity: DisplayIdentity? = null,
    toIdentity: DisplayIdentity? = null,
) {
    val resolvedFrom = fromIdentity ?: DisplayIdentity.Player(fromPlayerId, fromPlayerName)
    val resolvedTo = toIdentity ?: when {
        toPlayerId != null -> DisplayIdentity.Player(toPlayerId, toPlayerName)
        toPlayerName == DisplayIdentity.Bank.label -> DisplayIdentity.Bank
        else -> DisplayIdentity.Player(toPlayerId, toPlayerName)
    }
    DisplayIdentityTransferRow(
        from = resolvedFrom,
        to = resolvedTo,
        modifier = modifier,
        amount = amount,
        iconSize = iconSize,
        showFallbackPlayerIcon = true,
    )
}

@Composable
private fun PlayerIconImage(
    iconResId: Int,
    playerId: String?,
    playerName: String,
    size: PlayerIconSize,
) {
    Image(
        painter = painterResource(iconResId),
        contentDescription = null,
        modifier = Modifier
            .size(size.size)
            .semantics {
                contentDescription = playerDisplayIconDescription(playerId, playerName, size)
            },
        contentScale = ContentScale.Fit,
    )
}

fun playerDisplayIconDescription(
    playerId: String?,
    playerName: String,
    size: PlayerIconSize = PlayerIconSize.Normal,
): String {
    val label = "${PlayerIconRegistry.iconLabel(playerId)}, Player $playerName"
    return if (size == PlayerIconSize.Large) "Large $label" else label
}
