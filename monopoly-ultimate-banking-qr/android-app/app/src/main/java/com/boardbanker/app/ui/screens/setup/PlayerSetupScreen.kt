package com.boardbanker.app.ui.screens.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.validation.PlayerNameRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSetupScreen(
    viewModel: GameSetupViewModel,
    onScanPlayerCard: () -> Unit,
    onNavigateToGame: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GameSetupEvent.NavigateToGame -> onNavigateToGame()
                GameSetupEvent.NavigateHome -> onNavigateHome()
                is GameSetupEvent.PlayerRegistered -> Unit
            }
        }
    }

    uiState.pendingRegistration?.let { pending ->
        PlayerNameEntryDialog(
            title = "PLAYER DETAILS",
            playerId = pending.playerId,
            tokenName = pending.tokenName,
            initialName = "",
            confirmLabel = BankingActionLabels.confirm("ADD PLAYER"),
            onConfirm = viewModel::confirmPendingRegistration,
            onCancel = viewModel::cancelPendingRegistration,
        )
    }

    uiState.pendingNameEdit?.let { pending ->
        PlayerNameEntryDialog(
            title = "EDIT PLAYER NAME",
            playerId = pending.playerId,
            tokenName = pending.tokenName,
            initialName = pending.currentName,
            confirmLabel = BankingActionLabels.confirm("SAVE NAME"),
            onConfirm = viewModel::confirmEditPlayerName,
            onCancel = viewModel::cancelEditPlayerName,
        )
    }

    if (uiState.showCancelConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancelConfirm,
            title = { Text("Cancel this game?") },
            text = {
                Text(
                    "Registered players and this saved setup will be removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCancelGame) {
                    Text("CANCEL GAME")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCancelConfirm) {
                    Text("KEEP GAME")
                }
            },
        )
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("Player Setup") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) {
                    Text("OK")
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NEW GAME") }) },
    ) { innerPadding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Scan 2–4 Player cards",
                style = MaterialTheme.typography.titleMedium,
            )

            val allPlayerSlots = listOf("USR_01", "USR_02", "USR_03", "USR_04")
            allPlayerSlots.forEach { playerId ->
                val registered = uiState.registeredPlayers.find { it.playerId == playerId }
                val displayName = registered?.playerName ?: definitionsFallbackName(playerId)
                val prefix = if (registered != null) "✓" else "□"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (registered != null) {
                                Modifier.clickable { viewModel.startEditPlayerName(playerId) }
                            } else {
                                Modifier
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(prefix, style = MaterialTheme.typography.bodyLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlayerIdentity(
                            playerId = playerId,
                            playerName = displayName,
                            iconSize = PlayerIconSize.Normal,
                        )
                        registered?.let {
                            Text(formatMoney(it.balance), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Tap to edit name",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Players: ${uiState.playerCount} / ${uiState.maxPlayers}",
                style = MaterialTheme.typography.titleSmall,
            )

            Button(
                onClick = onScanPlayerCard,
                enabled = uiState.canAddPlayer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (uiState.canAddPlayer) {
                        "SCAN PLAYER CARD"
                    } else {
                        "MAXIMUM PLAYERS REACHED"
                    },
                )
            }

            BankingActionBar(
                confirmLabel = BankingActionLabels.confirm("START GAME"),
                onConfirm = viewModel::startGame,
                confirmEnabled = uiState.canStartGame,
                cancelLabel = BankingActionLabels.cancel("CANCEL GAME"),
                onCancel = viewModel::requestCancelGame,
            )
        }
    }
}

@Composable
private fun PlayerNameEntryDialog(
    title: String,
    playerId: String,
    tokenName: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var nameInput by remember(playerId, initialName) { mutableStateOf(initialName) }
    val trimmedLength = PlayerNameRules.normalize(nameInput).length

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CardFrontImage(cardId = playerId)
                PlayerIdentity(
                    playerId = playerId,
                    playerName = tokenName,
                    iconSize = PlayerIconSize.Large,
                    vertical = true,
                )
                Text(tokenName, style = MaterialTheme.typography.titleMedium)
                Text("Player Name", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { value ->
                        val withoutNewlines = value.replace("\n", "")
                        if (withoutNewlines.length <= PlayerNameRules.MAX_LENGTH) {
                            nameInput = withoutNewlines
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (trimmedLength in 1..PlayerNameRules.MAX_LENGTH) {
                                onConfirm(nameInput)
                            }
                        },
                    ),
                )
                Text(
                    "Maximum ${PlayerNameRules.MAX_LENGTH} characters",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "$trimmedLength / ${PlayerNameRules.MAX_LENGTH}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BankingActionBar(
                    confirmLabel = confirmLabel,
                    onConfirm = { onConfirm(nameInput) },
                    confirmEnabled = trimmedLength in 1..PlayerNameRules.MAX_LENGTH,
                    cancelLabel = BankingActionLabels.cancel("CANCEL"),
                    onCancel = onCancel,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

private fun definitionsFallbackName(playerId: String): String = when (playerId) {
    "USR_01" -> "Car"
    "USR_02" -> "Helicopter"
    "USR_03" -> "Ship"
    "USR_04" -> "Aeroplane"
    else -> "Player"
}
