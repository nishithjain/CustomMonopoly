package com.boardbanker.app.ui.screens.debugpreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.debugpreset.DebugPresetLoadResult
import com.boardbanker.app.debugpreset.DebugPresetExportResult
import com.boardbanker.app.debugpreset.DebugPresetPlayerSummary
import com.boardbanker.app.debugpreset.DebugPresetProvider
import com.boardbanker.app.debugpreset.DebugPresetSummary
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.CommonFilledActionButton
import com.boardbanker.app.ui.components.IconLabelRow
import kotlinx.coroutines.launch

@Composable
fun DebugPresetScreen(
    provider: DebugPresetProvider,
    onStarted: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var presets by remember { mutableStateOf<List<DebugPresetSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<DebugPresetSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<com.boardbanker.core.model.GameSession?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text == null) error = "Could not read the selected JSON file"
            else when (val result = provider.importJson(text)) {
                is DebugPresetLoadResult.Success -> {
                    selectedSession = result.session
                    selected = result.summary
                    error = null
                }
                is DebugPresetLoadResult.Failure -> error = result.message
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        pendingExport = null
    }

    fun loadSelected(preset: DebugPresetSummary) {
        loading = true
        error = null
        scope.launch {
            when (val result = provider.load(preset.presetId)) {
                is DebugPresetLoadResult.Success -> {
                    selected = result.summary ?: preset
                    selectedSession = result.session
                }
                is DebugPresetLoadResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun startSelected(forceReplace: Boolean = false) {
        val preset = selected ?: return
        if (provider.hasActiveSavedGame() && !forceReplace) {
            showReplaceConfirmation = true
            return
        }
        starting = true
        error = null
        scope.launch {
            val loaded = selectedSession?.let { DebugPresetLoadResult.Success(it, preset) }
                ?: provider.load(preset.presetId)
            when (loaded) {
                is DebugPresetLoadResult.Failure -> {
                    error = loaded.message
                    starting = false
                }
                is DebugPresetLoadResult.Success -> when (val result = provider.start(loaded.session)) {
                    is DebugPresetLoadResult.Failure -> {
                        error = result.message
                        starting = false
                    }
                    is DebugPresetLoadResult.Success -> onStarted()
                }
            }
        }
    }

    LaunchedEffect(provider) {
        presets = provider.discover()
        loading = false
    }

    if (showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmation = false },
            title = { Text("Replace the current game with this debug state?") },
            text = { Text("The current saved game will be replaced after the debug state passes validation.") },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirmation = false
                    startSelected(forceReplace = true)
                }) { Text("REPLACE") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmation = false }) { Text("CANCEL") }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug Game Presets", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Import Game State JSON") }
                Button(onClick = {
                    scope.launch {
                        when (val result = provider.exportCurrent()) {
                            is DebugPresetExportResult.Success -> {
                                pendingExport = result.json
                                exportLauncher.launch("debug-game-state.json")
                            }
                            is DebugPresetExportResult.Failure -> error = result.message
                        }
                    }
                }) { Text("Export Current Game State") }
            }
            Text("Bundled Game States", style = MaterialTheme.typography.titleMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading || starting) {
                CircularProgressIndicator()
            } else if (selected == null) {
                if (presets.isEmpty()) Text("No debug presets were found.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it.presetId }) { preset ->
                        Button(
                            onClick = { loadSelected(preset) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(preset.name)
                                Text("${preset.editionId} - ${preset.players.size} players")
                            }
                        }
                    }
                }
            } else {
                Text(selected!!.name, style = MaterialTheme.typography.titleLarge)
                Text(selected!!.description)
                Text("Edition: ${selected!!.editionId}")
                Text("Start destination: ${selected!!.startDestination}")
                selected!!.pendingDebt?.let { Text("Pending debt: $it") }
                selected!!.players.forEach { player -> PlayerSummary(player) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommonFilledActionButton(
                        icon = CommonUiIcon.START_GAME,
                        label = "START DEBUG GAME",
                        onClick = ::startSelected,
                    )
                    TextButton(onClick = { selected = null; error = null }) {
                        IconLabelRow(icon = CommonUiIcon.CANCEL, label = "CANCEL")
                    }
                }
            }
            if (selected == null) {
                TextButton(onClick = onCancel) {
                    IconLabelRow(icon = CommonUiIcon.BACK, label = "CANCEL")
                }
            }
        }
    }
}

@Composable
private fun PlayerSummary(player: DebugPresetPlayerSummary) {
    Text(
        text = "${player.name} - ${player.token}: ${player.balance} | " +
            "${player.propertyCount} properties, ${player.energyGridCount} Energy Grids",
        style = MaterialTheme.typography.bodyLarge,
    )
}
