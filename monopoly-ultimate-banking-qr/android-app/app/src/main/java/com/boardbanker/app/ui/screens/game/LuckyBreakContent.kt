package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.gameplay.presentation.DiceGambleStatus
import com.boardbanker.app.gameplay.presentation.DiceGambleUiState
import com.boardbanker.app.ui.components.DieFace

@Composable
fun LuckyBreakContent(
    state: DiceGambleUiState,
    onRollDice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.eventName,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = state.instruction,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = state.playerName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = state.attemptLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DieFace(value = state.dieOne, label = "Die one")
            DieFace(value = state.dieTwo, label = "Die two")
        }
        Text(
            text = "Jackpot: ${state.jackpotText}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Penalty: ${state.penaltyText}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.status == DiceGambleStatus.AWAITING_DEBT_RESOLUTION) {
            Text(
                text = "Insufficient funds. Resolve the debt to continue.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Button(
                onClick = onRollDice,
                enabled = state.rollEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (state.status) {
                        DiceGambleStatus.ROLLING -> "Rolling..."
                        else -> "Roll Dice"
                    },
                )
            }
        }
    }
}
