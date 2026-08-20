package com.boardbanker.app.ui.screens.persistence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boardbanker.app.BankingQrApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistenceDebugScreen(
    app: BankingQrApplication,
    onBack: () -> Unit,
    viewModel: PersistenceDebugViewModel = viewModel(
        factory = PersistenceDebugViewModelFactory(
            definitions = app.gameDefinitions,
            repository = app.gameSessionRepository,
            committedStore = app.committedGameSessionStore,
        ),
    ),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("PERSISTENCE TEST") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { viewModel.createTestSession() }, modifier = Modifier.fillMaxWidth()) {
                Text("CREATE TEST SESSION")
            }
            Button(onClick = { viewModel.saveSession() }, modifier = Modifier.fillMaxWidth()) {
                Text("SAVE SESSION")
            }
            Button(onClick = { viewModel.loadSession() }, modifier = Modifier.fillMaxWidth()) {
                Text("LOAD SESSION")
            }
            Button(onClick = { viewModel.deleteSession() }, modifier = Modifier.fillMaxWidth()) {
                Text("DELETE SESSION")
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}
