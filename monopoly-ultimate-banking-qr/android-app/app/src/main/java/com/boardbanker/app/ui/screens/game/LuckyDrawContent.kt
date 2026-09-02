package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.gameplay.presentation.EventDrawUiState

@Composable
fun LuckyDrawContent(
    state: EventDrawUiState,
    onScanEventCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Lucky Draw",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = state.instruction,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = state.actingPlayerName,
            style = MaterialTheme.typography.titleMedium,
        )
        state.chainProgressText?.let { progress ->
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onScanEventCard,
            enabled = state.scanEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.scanEnabled) "Scan Event Card" else "Scanning...")
        }
    }
}
