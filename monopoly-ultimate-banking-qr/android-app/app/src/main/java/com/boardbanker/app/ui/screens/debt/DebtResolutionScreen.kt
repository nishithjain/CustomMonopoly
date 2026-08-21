package com.boardbanker.app.ui.screens.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerTransferRow
import com.boardbanker.app.ui.components.PlayerIconSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtResolutionScreen(
    viewModel: DebtResolutionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onOpenPropertyScanner: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DebtResolutionEvent.NavigateBack -> onNavigateBack()
                DebtResolutionEvent.NavigateToGameOver -> onNavigateToGameOver()
                DebtResolutionEvent.OpenPropertyScanner -> onOpenPropertyScanner()
            }
        }
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Debt") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("DEBT PAYMENT") }) }) { innerPadding ->
        if (uiState.commandInFlight && uiState.result == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.result == null) {
                Text("DEBT PAYMENT", style = MaterialTheme.typography.titleMedium)
                PlayerTransferRow(
                    fromPlayerId = uiState.debtorPlayerId,
                    fromPlayerName = uiState.debtorName,
                    toPlayerId = uiState.creditorPlayerId,
                    toPlayerName = uiState.creditorName,
                    iconSize = PlayerIconSize.Normal,
                )
                Text(
                    buildString {
                        append("Amount due:\n${viewModel.money(uiState.amountDue)}\n\n")
                        append("Available cash:\n${viewModel.money(uiState.availableCash)}\n\n")
                        append("Remaining after cash:\n${viewModel.money(uiState.remainingAfterCash)}")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Select Properties to cover ${viewModel.money(uiState.remainingAfterCash)}:")
                uiState.properties.forEach { property ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = property.selected,
                            onCheckedChange = { viewModel.onToggleProperty(property.propertyId) },
                        )
                        Text("${property.propertyName}       ${viewModel.money(property.debtValue)}")
                    }
                }
                Button(onClick = viewModel::onScanPropertyRequested, modifier = Modifier.fillMaxWidth()) {
                    Text("SCAN PROPERTY TO USE FOR DEBT")
                }
                Button(onClick = viewModel::onSettleSelected, modifier = Modifier.fillMaxWidth()) {
                    Text("SETTLE WITH SELECTED PROPERTY")
                }
                Button(onClick = viewModel::onCheckBankruptcy, modifier = Modifier.fillMaxWidth()) {
                    Text("CHECK BANKRUPTCY")
                }
            } else {
                GameplayResultPresentation(result = uiState.result!!)
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("DONE"),
                    onConfirm = viewModel::onDone,
                )
            }
        }
    }
}
