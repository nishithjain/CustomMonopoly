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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
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
                entries.forEachIndexed { index, entry ->
                    TransactionHistoryEntryRow(entry = entry)
                    if (index < entries.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}

@Composable
private fun TransactionHistoryEntryRow(entry: HistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (entry.undone) TextDecoration.LineThrough else null,
                )
                if (entry.undone) {
                    Text(
                        "UNDONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(entry.time, style = MaterialTheme.typography.labelSmall)
        }
        entry.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        entry.propertyName?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        entry.lines.forEach { line ->
            TransactionHistoryLineRow(line = line)
        }
    }
}

@Composable
private fun TransactionHistoryLineRow(line: HistoryLine) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${line.label}:", style = MaterialTheme.typography.labelMedium)
            if (line.fromPlayerName != null && line.toPlayerName != null) {
                CompactParty(playerId = line.fromPlayerId, name = line.fromPlayerName)
                Text("→", style = MaterialTheme.typography.bodyMedium)
                CompactParty(playerId = line.toPlayerId, name = line.toPlayerName)
            } else if (line.playerName != null) {
                CompactParty(playerId = line.playerId, name = line.playerName)
            }
            line.detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        line.propertyName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompactParty(playerId: String?, name: String) {
    if (playerId == null) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
    } else {
        PlayerIdentity(
            playerId = playerId,
            playerName = name,
            iconSize = PlayerIconSize.Small,
        )
    }
}
