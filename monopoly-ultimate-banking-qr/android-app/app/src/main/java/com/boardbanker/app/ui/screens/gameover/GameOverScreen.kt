package com.boardbanker.app.ui.screens.gameover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.ui.components.GameplayResultPresentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverScreen(
    viewModel: GameOverViewModel,
    onNavigateHome: () -> Unit,
    onNewGame: () -> Unit,
) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("GAME OVER") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            result?.let {
                GameplayResultPresentation(
                    result = it,
                    showLargePrimaryPlayer = true,
                )
            }
            Button(onClick = onNavigateHome, modifier = Modifier.fillMaxWidth()) {
                Text("RETURN HOME")
            }
            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                Text("NEW GAME")
            }
        }
    }
}
