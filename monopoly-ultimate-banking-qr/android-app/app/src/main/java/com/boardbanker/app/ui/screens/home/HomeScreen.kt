package com.boardbanker.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.BuildConfig
import com.boardbanker.core.model.GameStatus

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onResumeSetup: () -> Unit,
    onResumeGame: () -> Unit,
    onTestQrScanner: () -> Unit = {},
    onTestPersistence: () -> Unit = {},
    viewModel: HomeViewModel,
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    var showNewGameConfirm by rememberSaveable { mutableStateOf(false) }

    if (showNewGameConfirm) {
        AlertDialog(
            onDismissRequest = { showNewGameConfirm = false },
            title = { Text("Start a new game?") },
            text = {
                Text(
                    "A saved game already exists.\n\n" +
                        "Starting a new game will remove the current saved game.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewGameConfirm = false
                        viewModel.deleteSavedGame(onDeleted = onNewGame)
                    },
                ) {
                    Text("START NEW GAME")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameConfirm = false }) {
                    Text("CANCEL")
                }
            },
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Banking QR",
                style = MaterialTheme.typography.headlineLarge,
            )

            if (homeState.definitionsError != null) {
                Text(
                    text = "Game data failed to load: ${homeState.definitionsError}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (homeState.incompatibleEdition != null) {
                Text(
                    text = homeState.incompatibleEdition!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Button(
                onClick = {
                    if (homeState.hasSavedGame) {
                        showNewGameConfirm = true
                    } else {
                        onNewGame()
                    }
                },
                enabled = homeState.definitionsError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .semantics { contentDescription = "Start new game" },
            ) {
                Text("NEW GAME")
            }

            val resumeEnabled = homeState.incompatibleEdition == null && homeState.definitionsError == null
            when (homeState.savedGameStatus) {
                GameStatus.SETUP -> {
                    Button(
                        onClick = onResumeSetup,
                        enabled = resumeEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .semantics { contentDescription = "Resume setup" },
                    ) {
                        Text("RESUME SETUP")
                    }
                }
                GameStatus.ACTIVE -> {
                    Button(
                        onClick = onResumeGame,
                        enabled = resumeEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .semantics { contentDescription = "Resume game" },
                    ) {
                        Text("RESUME GAME")
                    }
                }
                else -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .semantics { contentDescription = "Resume saved game" },
                    ) {
                        Text("RESUME GAME (no save)")
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Button(
                    onClick = onTestQrScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics { contentDescription = "Test QR scanner" },
                ) {
                    Text("TEST QR SCANNER")
                }
                Button(
                    onClick = onTestPersistence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics { contentDescription = "Test persistence" },
                ) {
                    Text("TEST PERSISTENCE")
                }
            }
        }
    }
}
