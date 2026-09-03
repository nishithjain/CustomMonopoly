package com.boardbanker.app.scanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boardbanker.app.BankingQrApplication
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.core.model.EditionCatalogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestQrScannerScreen(
    app: BankingQrApplication,
    onBack: () -> Unit,
) {
    val catalog = remember(app) {
        runCatching { app.editionRepository.loadEditionCatalog() }.getOrNull()
    }
    val editions = catalog?.editions.orEmpty()
        .filter { it.enabled }
        .sortedBy { it.name }
    val initialEditionId = remember(catalog, editions) {
        catalog?.defaultEditionId?.takeIf { id -> editions.any { it.editionId == id } }
            ?: editions.firstOrNull()?.editionId
            ?: "uk"
    }
    var selectedEditionId by rememberSaveable(initialEditionId) { mutableStateOf(initialEditionId) }
    val selectedEditionName = editions.firstOrNull { it.editionId == selectedEditionId }?.name
        ?: selectedEditionId
    var editionMenuExpanded by remember { mutableStateOf(false) }
    val definitions = remember(selectedEditionId) {
        runCatching { app.editionRepository.load(selectedEditionId) }.getOrNull()
    }

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Choose the edition that matches the physical cards you are scanning.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (editions.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = editionMenuExpanded,
                    onExpandedChange = { editionMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedEditionName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Card edition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editionMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = editionMenuExpanded,
                        onDismissRequest = { editionMenuExpanded = false },
                    ) {
                        editions.forEach { edition ->
                            EditionMenuItem(
                                edition = edition,
                                onSelected = {
                                    selectedEditionId = edition.editionId
                                    editionMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            } else if (selectedEditionName.isNotBlank()) {
                Text(
                    text = "Edition: $selectedEditionName",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (definitions == null) {
            Text(
                text = "Unable to load edition data for '$selectedEditionId'.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            ScannerScreen(
                onBack = onBack,
                scanRequest = ScanRequest.gameCard(),
                definitions = definitions,
                viewModelKey = selectedEditionId,
            )
        }
    }
}

@Composable
private fun EditionMenuItem(
    edition: EditionCatalogEntry,
    onSelected: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(edition.name) },
        onClick = onSelected,
    )
}
