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
) {
    val iconResId = PlayerIconRegistry.iconResId(playerId)
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
                    size = iconSize,
                )
            }
            Text(playerName, style = MaterialTheme.typography.titleMedium)
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
                    size = iconSize,
                )
            }
            Text(playerName, style = MaterialTheme.typography.bodyLarge)
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
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerIdentity(
            playerId = fromPlayerId,
            playerName = fromPlayerName,
            iconSize = iconSize,
        )
        Text("→", style = MaterialTheme.typography.titleMedium)
        if (toPlayerId == null && toPlayerName == "Bank") {
            Text("Bank", style = MaterialTheme.typography.bodyLarge)
        } else {
            PlayerIdentity(
                playerId = toPlayerId,
                playerName = toPlayerName,
                iconSize = iconSize,
            )
        }
    }
}

@Composable
private fun PlayerIconImage(
    iconResId: Int,
    playerId: String?,
    size: PlayerIconSize,
) {
    Image(
        painter = painterResource(iconResId),
        contentDescription = null,
        modifier = Modifier
            .size(size.size)
            .semantics {
                contentDescription = playerDisplayIconDescription(playerId, size)
            },
        contentScale = ContentScale.Fit,
    )
}

private fun playerDisplayIconDescription(playerId: String?, size: PlayerIconSize): String {
    val label = when (playerId) {
        "USR_01" -> "Car player icon"
        "USR_02" -> "Helicopter player icon"
        "USR_03" -> "Ship player icon"
        "USR_04" -> "Aeroplane player icon"
        else -> "Player icon"
    }
    return if (size == PlayerIconSize.Large) "Large $label" else label
}
