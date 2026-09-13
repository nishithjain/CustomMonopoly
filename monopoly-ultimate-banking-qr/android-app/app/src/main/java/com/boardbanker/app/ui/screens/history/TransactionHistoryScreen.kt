package com.boardbanker.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.BackActionButton
import com.boardbanker.app.ui.components.CommonUiIconImage
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.components.DisplayIdentityRow
import com.boardbanker.app.ui.components.DisplayIdentityTransferRow
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.TopBarIconTitle
import com.boardbanker.core.model.GameDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    sessionManager: ActiveGameSessionManager,
    definitions: GameDefinitions,
    onBack: () -> Unit,
) {
    val entries = remember(sessionManager.currentSession()) {
        val session = sessionManager.currentSession() ?: return@remember emptyList()
        TransactionHistoryEntries.build(session, definitions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TopBarIconTitle(
                        icon = CommonUiIcon.RECENT_BANKING,
                        title = "RECENT BANKING",
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) {
                Text("No transactions yet.", style = MaterialTheme.typography.bodyLarge)
            } else {
                entries.forEach { entry ->
                    TransactionHistoryEntryCard(entry = entry)
                }
            }
            BackActionButton(onClick = onBack)
        }
    }
}

@Composable
private fun TransactionHistoryEntryCard(entry: HistoryEntry) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    entry.entryIcon?.let { icon ->
                        CommonUiIconImage(
                            icon = icon,
                            size = 20.dp,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (entry.undone) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.undone) {
                        Text(
                            text = "UNDONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = entry.time,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            entry.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HistoryDetailRow(detail = entry.detail)
        }
    }
}

@Composable
private fun HistoryDetailRow(detail: HistoryDetail) {
    when (detail) {
        is HistoryDetail.PlayerTransfer -> DisplayIdentityTransferRow(
            from = detail.from,
            to = detail.to,
            amount = detail.amount,
            iconSize = PlayerIconSize.Compact,
            showFallbackPlayerIcon = true,
        )
        is HistoryDetail.RentLevelChange -> InlineRentLevelChangeDetail(detail = detail)
        is HistoryDetail.RentWaived -> InlineRentWaivedDetail(detail = detail)
        is HistoryDetail.PlayerMention -> InlinePlayerMentionDetail(detail = detail)
        is HistoryDetail.LuckyBreakResolution -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommonUiIconImage(
                    icon = CommonUiIcon.DICE,
                    size = 20.dp,
                    contentDescription = null,
                )
                Text(
                    text = detail.diceDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            DisplayIdentityTransferRow(
                from = detail.transfer.from,
                to = detail.transfer.to,
                amount = detail.transfer.amount,
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
        }
        is HistoryDetail.DebtPropertySettlement -> {
            DisplayIdentityTransferRow(
                from = detail.transfer.from,
                to = detail.transfer.to,
                amount = detail.transfer.amount,
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
            Text(
                text = detail.valueLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is HistoryDetail.EventMultiPlayerTransfer -> {
            DisplayIdentityRow(
                identity = DisplayIdentity.Player(detail.payerPlayerId, detail.payerName),
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
            Text(
                text = detail.summaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            detail.transfers.forEach { transfer ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CommonUiIconImage(
                        icon = CommonUiIcon.MONEY_TRANSFER,
                        size = 20.dp,
                        contentDescription = null,
                    )
                    DisplayIdentityTransferRow(
                        from = transfer.from,
                        to = transfer.to,
                        amount = transfer.amount,
                        iconSize = PlayerIconSize.Compact,
                        showFallbackPlayerIcon = true,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Text(
                text = detail.totalLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is HistoryDetail.EventBankruptcy -> {
            DisplayIdentityRow(
                identity = DisplayIdentity.Player(detail.playerId, detail.playerName),
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
            Text(
                text = detail.summaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is HistoryDetail.RentDebtSettled -> {
            DisplayIdentityTransferRow(
                from = detail.transfer.from,
                to = detail.transfer.to,
                amount = detail.transfer.amount,
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
            Text(
                text = buildString {
                    append("Amount due: ${detail.amountDue}")
                    append("\nCash used: ${detail.cashUsed} • Property value used: ${detail.propertyValueUsed}")
                    append("\nRemaining due: ${detail.remainingDue}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is HistoryDetail.Text -> {
            Text(
                text = detail.value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlineRentWaivedDetail(detail: HistoryDetail.RentWaived) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Rent waived: ${detail.waivedAmount}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisplayIdentityRow(
                identity = DisplayIdentity.Player(detail.landingPlayerId, detail.landingPlayerName),
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail.propertyName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail.reason,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlinePlayerMentionDetail(detail: HistoryDetail.PlayerMention) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayIdentityRow(
            identity = DisplayIdentity.Player(detail.playerId, detail.playerName),
            iconSize = PlayerIconSize.Compact,
            showFallbackPlayerIcon = true,
        )
        detail.suffix?.let { suffix ->
            Text(
                text = ": $suffix",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlineRentLevelChangeDetail(detail: HistoryDetail.RentLevelChange) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayIdentityRow(
            identity = DisplayIdentity.Player(detail.playerId, detail.playerName),
            iconSize = PlayerIconSize.Compact,
            showFallbackPlayerIcon = true,
        )
        Text(":", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${detail.propertyName} ${detail.levelChangeText}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
