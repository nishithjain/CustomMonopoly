package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boardbanker.core.model.TurnKind

private val StandardButtonHeight = 56.dp
val ActiveGameEndTurnButtonHeight = StandardButtonHeight * 2

@Composable
fun ActiveGameHubActions(
    uiState: GameUiState,
    showGameTerminationActions: Boolean,
    onScanCard: () -> Unit,
    onGetOutOfJail: () -> Unit,
    onBankActions: () -> Unit,
    onEndTurn: () -> Unit,
    onEndGame: () -> Unit,
    onAbandonGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_game_hub_actions"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val turnLabel = when (uiState.turnKind) {
            TurnKind.EXTRA -> uiState.activePlayerName?.let { "$it's Extra Turn" }
            else -> uiState.activePlayerName?.let { "Current turn: $it" }
        }
        turnLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        uiState.jailResolutionMessage?.let { jailMessage ->
            Text(
                text = jailMessage,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Button(
            onClick = onScanCard,
            enabled = uiState.actionAvailability.scanCardEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardButtonHeight)
                .testTag("active_game_scan_card_button"),
        ) {
            Text("Scan Card")
        }

        if (uiState.actionAvailability.getOutOfJailEnabled) {
            Button(
                onClick = onGetOutOfJail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StandardButtonHeight),
            ) {
                Text("Get Out of Jail")
            }
        }

        OutlinedButton(
            onClick = onBankActions,
            enabled = uiState.actionAvailability.bankActionsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardButtonHeight)
                .testTag("active_game_bank_actions_button"),
        ) {
            Text("Bank Actions")
        }

        if (uiState.activePlayerId != null) {
            Button(
                onClick = onEndTurn,
                enabled = uiState.actionAvailability.endTurnEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActiveGameEndTurnButtonHeight)
                    .testTag("active_game_end_turn_button")
                    .semantics {
                        contentDescription = uiState.endTurnContentDescription
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("END TURN", style = MaterialTheme.typography.titleMedium)
                    uiState.endTurnSubtitle?.let { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            uiState.endTurnDisabledReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("active_game_end_turn_disabled_reason"),
                )
            }
        }

        if (showGameTerminationActions) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            OutlinedButton(
                onClick = onEndGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StandardButtonHeight)
                    .testTag("active_game_end_game_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("End Game")
            }
            OutlinedButton(
                onClick = onAbandonGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StandardButtonHeight)
                    .testTag("active_game_abandon_game_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Abandon Game")
            }
        }
    }
}
