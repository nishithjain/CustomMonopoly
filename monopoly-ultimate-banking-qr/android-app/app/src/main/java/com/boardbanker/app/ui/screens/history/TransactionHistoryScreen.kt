package com.boardbanker.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerTransferRow
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType

private data class TransactionHistoryEntry(
    val title: String,
    val fromPlayerId: String?,
    val fromDisplayName: String?,
    val toPlayerId: String?,
    val toDisplayName: String?,
    val playerId: String?,
    val playerDisplayName: String?,
    val propertyName: String?,
    val amount: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    sessionManager: ActiveGameSessionManager,
    definitions: GameDefinitions,
    onBack: () -> Unit,
) {
    val entries = remember(sessionManager.currentSession()) {
        val session = sessionManager.currentSession() ?: return@remember emptyList()
        session.transactions
            .filter { it.transactionType != TransactionType.UNDO }
            .takeLast(20)
            .reversed()
            .map { buildEntry(it, session, definitions) }
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
                    TransactionHistoryRow(entry = entry)
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}

@Composable
private fun TransactionHistoryRow(entry: TransactionHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(entry.title, style = MaterialTheme.typography.titleSmall)
        if (entry.fromDisplayName != null && entry.toDisplayName != null) {
            PlayerTransferRow(
                fromPlayerId = entry.fromPlayerId,
                fromPlayerName = entry.fromDisplayName,
                toPlayerId = entry.toPlayerId,
                toPlayerName = entry.toDisplayName,
                iconSize = PlayerIconSize.Small,
            )
        } else if (entry.playerId != null && entry.playerDisplayName != null) {
            PlayerIdentity(
                playerId = entry.playerId,
                playerName = entry.playerDisplayName,
                iconSize = PlayerIconSize.Small,
            )
        }
        entry.propertyName?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        entry.amount?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun buildEntry(
    tx: Transaction,
    session: com.boardbanker.core.model.GameSession,
    definitions: GameDefinitions,
): TransactionHistoryEntry {
    val title = tx.transactionType.name.replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.titlecase() }
    val fromId = tx.fromEntity?.takeIf { it != EntityRef.BANK }
    val toId = tx.toEntity?.takeIf { it != EntityRef.BANK }
    val fromName = tx.fromEntity?.let { entityName(it, session, definitions) }
    val toName = tx.toEntity?.let { entityName(it, session, definitions) }
    val property = tx.propertyId?.let { definitions.properties[it]?.name }
    val playerId = tx.playerId
    val playerName = playerId?.let { PlayerDisplayNames.displayName(session, it, definitions) }
    val amount = tx.amount?.let { "M$it" }
    return TransactionHistoryEntry(
        title = title,
        fromPlayerId = fromId,
        fromDisplayName = fromName,
        toPlayerId = toId,
        toDisplayName = toName,
        playerId = playerId,
        playerDisplayName = playerName,
        propertyName = property,
        amount = amount,
    )
}

private fun entityName(entity: String, session: com.boardbanker.core.model.GameSession, definitions: GameDefinitions): String =
    if (entity == EntityRef.BANK) {
        "Bank"
    } else {
        PlayerDisplayNames.displayName(session, entity, definitions)
    }
