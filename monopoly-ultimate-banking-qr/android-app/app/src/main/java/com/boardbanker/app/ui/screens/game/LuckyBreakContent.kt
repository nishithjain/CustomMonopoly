package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.gameplay.presentation.DiceGambleStatus
import com.boardbanker.app.gameplay.presentation.DiceGambleUiState
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.DieFace
import com.boardbanker.app.ui.components.IconLabelRow
import com.boardbanker.core.model.DiceGambleMode

@Composable
fun LuckyBreakContent(
    state: DiceGambleUiState,
    onSelectInAppMode: () -> Unit,
    onSelectPhysicalMode: () -> Unit,
    onRollDice: () -> Unit,
    onPhysicalJackpot: () -> Unit,
    onPhysicalPenalty: () -> Unit,
    onConfirmPhysical: () -> Unit,
    onBackFromPhysical: () -> Unit,
    onCancelPhysicalConfirm: () -> Unit,
    onContinue: () -> Unit,
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
        when {
            state.showContinue -> CompletedContent(state, onContinue)
            state.status == DiceGambleStatus.SELECT_MODE -> ModeSelectionContent(
                state = state,
                onSelectInAppMode = onSelectInAppMode,
                onSelectPhysicalMode = onSelectPhysicalMode,
            )
            state.mode == DiceGambleMode.PHYSICAL -> PhysicalDiceContent(
                state = state,
                onPhysicalJackpot = onPhysicalJackpot,
                onPhysicalPenalty = onPhysicalPenalty,
                onConfirmPhysical = onConfirmPhysical,
                onBackFromPhysical = onBackFromPhysical,
                onCancelPhysicalConfirm = onCancelPhysicalConfirm,
            )
            else -> InAppDiceContent(state, onRollDice)
        }
    }
}

@Composable
private fun ModeSelectionContent(
    state: DiceGambleUiState,
    onSelectInAppMode: () -> Unit,
    onSelectPhysicalMode: () -> Unit,
) {
    Text(
        text = state.instruction,
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = state.playerName,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = "Jackpot: ${state.jackpotText}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "Penalty: ${state.penaltyText}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onSelectInAppMode, modifier = Modifier.fillMaxWidth()) {
        IconLabelRow(icon = CommonUiIcon.DICE, label = "Roll Dice in App")
    }
    OutlinedButton(onClick = onSelectPhysicalMode, modifier = Modifier.fillMaxWidth()) {
        IconLabelRow(icon = CommonUiIcon.DICE, label = "Use Physical Dice")
    }
}

@Composable
private fun InAppDiceContent(
    state: DiceGambleUiState,
    onRollDice: () -> Unit,
) {
    Text(
        text = state.instruction,
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = state.playerName,
        style = MaterialTheme.typography.titleMedium,
    )
    if (state.attemptLabel.isNotBlank()) {
        Text(
            text = state.attemptLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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
    when (state.status) {
        DiceGambleStatus.AWAITING_DEBT_RESOLUTION -> {
            Text(
                text = "Insufficient funds. Resolve the debt to continue.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            Button(
                onClick = onRollDice,
                enabled = state.rollEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconLabelRow(icon = CommonUiIcon.DICE, label = state.rollButtonLabel)
            }
        }
    }
}

@Composable
private fun PhysicalDiceContent(
    state: DiceGambleUiState,
    onPhysicalJackpot: () -> Unit,
    onPhysicalPenalty: () -> Unit,
    onConfirmPhysical: () -> Unit,
    onBackFromPhysical: () -> Unit,
    onCancelPhysicalConfirm: () -> Unit,
) {
    Text(
        text = state.physicalInstruction,
        style = MaterialTheme.typography.bodyLarge,
    )
    when (state.status) {
        DiceGambleStatus.PHYSICAL_CONFIRM_JACKPOT,
        DiceGambleStatus.PHYSICAL_CONFIRM_PENALTY,
        -> {
            state.physicalConfirmMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Button(
                onClick = onConfirmPhysical,
                enabled = state.rollEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirm")
            }
            OutlinedButton(
                onClick = onCancelPhysicalConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconLabelRow(icon = CommonUiIcon.BACK, label = "Back")
            }
        }
        else -> {
            Button(onClick = onPhysicalJackpot, modifier = Modifier.fillMaxWidth()) {
                IconLabelRow(icon = CommonUiIcon.JACKPOT, label = state.physicalJackpotLabel)
            }
            Button(onClick = onPhysicalPenalty, modifier = Modifier.fillMaxWidth()) {
                IconLabelRow(icon = CommonUiIcon.PENALTY, label = state.physicalPenaltyLabel)
            }
            OutlinedButton(onClick = onBackFromPhysical, modifier = Modifier.fillMaxWidth()) {
                IconLabelRow(icon = CommonUiIcon.BACK, label = "Back")
            }
        }
    }
}

@Composable
private fun CompletedContent(
    state: DiceGambleUiState,
    onContinue: () -> Unit,
) {
    state.outcomeHeadline?.let { headline ->
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    state.outcomeMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (state.dieOne != null && state.dieTwo != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DieFace(value = state.dieOne, label = "Die one")
            DieFace(value = state.dieTwo, label = "Die two")
        }
    }
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
    }
}
