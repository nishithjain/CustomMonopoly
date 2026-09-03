package com.boardbanker.app.ui.screens.resume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boardbanker.app.BankingQrApplication
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeGameScreen(
    app: BankingQrApplication,
    onBack: () -> Unit,
    viewModel: ResumeGameViewModel = viewModel(
        factory = ResumeGameViewModelFactory(
            repository = app.gameSessionRepository,
            committedStore = app.committedGameSessionStore,
            definitions = app.defaultGameDefinitions,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadSavedGame()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("RESUME GAME") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = uiState) {
                ResumeUiState.Loading -> CircularProgressIndicator()
                is ResumeUiState.Found -> {
                    Text("SAVED GAME", style = MaterialTheme.typography.headlineSmall)
                    Text("Players:", style = MaterialTheme.typography.titleMedium)
                    state.players.forEach { player ->
                        PlayerIdentity(
                            playerId = player.playerId,
                            playerName = player.playerName,
                            iconSize = PlayerIconSize.Normal,
                        )
                    }
                    Text("Transactions: ${state.transactionCount}")
                    Text("Status: ${state.status}")
                    Text("Last saved:\n${state.lastSavedText}")
                    Button(onClick = { viewModel.loadIntoStore() }, modifier = Modifier.fillMaxWidth()) {
                        Text("LOAD")
                    }
                    Button(onClick = { viewModel.deleteSave(onDeleted = onBack) }, modifier = Modifier.fillMaxWidth()) {
                        Text("DELETE SAVE")
                    }
                }
                is ResumeUiState.Error -> {
                    Text("Unable to load saved game", style = MaterialTheme.typography.headlineSmall)
                    Text(state.message)
                }
                ResumeUiState.Deleted -> Text("Saved game deleted.")
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}

class ResumeGameViewModelFactory(
    private val repository: GameSessionRepository,
    private val committedStore: com.boardbanker.app.persistence.CommittedGameSessionStore,
    private val definitions: com.boardbanker.core.model.GameDefinitions,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResumeGameViewModel::class.java)) {
            return ResumeGameViewModel(repository, committedStore, definitions) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
