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
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity
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
        topBar = { TopAppBar(title = { Text("RECENT BANKING") }) },
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
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
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
                    maxLines = 1,
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
        is HistoryDetail.PlayerTransfer -> InlinePlayerTransferDetail(detail = detail)
        is HistoryDetail.RentLevelChange -> InlineRentLevelChangeDetail(detail = detail)
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
private fun InlinePlayerTransferDetail(detail: HistoryDetail.PlayerTransfer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineParty(playerId = detail.fromPlayerId, name = detail.fromPlayerName)
        Text("→", style = MaterialTheme.typography.bodyMedium)
        InlineParty(playerId = detail.toPlayerId, name = detail.toPlayerName)
        if (detail.amount.isNotBlank()) {
            Text(
                text = detail.amount,
                style = MaterialTheme.typography.bodyMedium,
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
        InlineParty(playerId = detail.playerId, name = detail.playerName)
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

@Composable
private fun InlineParty(playerId: String?, name: String) {
    if (playerId == null) {
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        PlayerIdentity(
            playerId = playerId,
            playerName = name,
            iconSize = PlayerIconSize.Small,
        )
    }
}
