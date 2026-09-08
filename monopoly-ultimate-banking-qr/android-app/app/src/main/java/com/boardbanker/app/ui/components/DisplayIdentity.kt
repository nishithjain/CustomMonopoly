package com.boardbanker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonIconRegistry
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.player.PlayerIconRegistry

sealed class DisplayIdentity {
    abstract val label: String
    abstract val contentDescription: String

    data class Player(
        val playerId: String?,
        val playerName: String,
    ) : DisplayIdentity() {
        override val label: String = playerName
        override val contentDescription: String =
            "${PlayerIconRegistry.iconLabel(playerId)}, Player $playerName"
    }

    data object Bank : DisplayIdentity() {
        override val label: String = "Bank"
        override val contentDescription: String = "Bank"
    }

    data object Jail : DisplayIdentity() {
        override val label: String = "Jail"
        override val contentDescription: String = "Jail"
    }
}

@Composable
fun DisplayIdentityIcon(
    identity: DisplayIdentity,
    modifier: Modifier = Modifier,
    iconSize: PlayerIconSize = PlayerIconSize.Small,
    showFallbackPlayerIcon: Boolean = false,
) {
    when (identity) {
        is DisplayIdentity.Player -> {
            val iconResId = if (showFallbackPlayerIcon) {
                PlayerIconRegistry.iconResIdOrFallback(identity.playerId)
            } else {
                PlayerIconRegistry.iconResId(identity.playerId)
            }
            if (iconResId != null) {
                Image(
                    painter = painterResource(iconResId),
                    contentDescription = identity.contentDescription,
                    modifier = modifier.size(iconSize.size),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        DisplayIdentity.Bank -> {
            CommonUiIconImage(
                icon = CommonUiIcon.BANK,
                modifier = modifier,
                size = iconSize.size,
                contentDescription = identity.contentDescription,
            )
        }
        DisplayIdentity.Jail -> {
            CommonUiIconImage(
                icon = CommonUiIcon.JAIL,
                modifier = modifier,
                size = iconSize.size,
                contentDescription = identity.contentDescription,
            )
        }
    }
}

@Composable
fun DisplayIdentityRow(
    identity: DisplayIdentity,
    modifier: Modifier = Modifier,
    iconSize: PlayerIconSize = PlayerIconSize.Small,
    showFallbackPlayerIcon: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DisplayIdentityIcon(
            identity = identity,
            iconSize = iconSize,
            showFallbackPlayerIcon = showFallbackPlayerIcon,
        )
        Text(
            text = identity.label,
            style = if (iconSize == PlayerIconSize.Small) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DisplayIdentityTransferRow(
    from: DisplayIdentity,
    to: DisplayIdentity,
    modifier: Modifier = Modifier,
    amount: String? = null,
    iconSize: PlayerIconSize = PlayerIconSize.Small,
    showFallbackPlayerIcon: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayIdentityRow(
            identity = from,
            iconSize = iconSize,
            showFallbackPlayerIcon = showFallbackPlayerIcon,
        )
        Text("→", style = MaterialTheme.typography.bodyMedium)
        DisplayIdentityRow(
            identity = to,
            iconSize = iconSize,
            showFallbackPlayerIcon = showFallbackPlayerIcon,
        )
        if (!amount.isNullOrBlank()) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
