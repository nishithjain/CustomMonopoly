package com.boardbanker.app.ui.screens.playerdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsScreen(
    viewModel: PlayerDetailsViewModel,
    onNavigateBack: () -> Unit,
    onOpenPropertyScanner: () -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onContinueLocationOnActiveGame: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlayerDetailsEvent.NavigateBack -> onNavigateBack()
                PlayerDetailsEvent.OpenPropertyScanner -> onOpenPropertyScanner()
                PlayerDetailsEvent.NavigateToDebt -> onNavigateToDebt()
                PlayerDetailsEvent.NavigateToGameOver -> onNavigateToGameOver()
                PlayerDetailsEvent.ContinueLocationOnActiveGame -> onContinueLocationOnActiveGame()
            }
        }
    }

    uiState.selectedPropertyId?.let { propertyId ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPropertyPreview,
            title = { Text("PROPERTY") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardFrontImage(cardId = propertyId)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPropertyPreview) { Text("CLOSE") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PLAYER DETAILS") },
                navigationIcon = {
                    TextButton(onClick = viewModel::onBack) { Text("BACK") }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.commandInFlight) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
            PlayerIdentity(
                playerId = uiState.playerId,
                playerName = uiState.playerName,
                iconSize = PlayerIconSize.Large,
                vertical = true,
            )
            if (uiState.tokenName.isNotBlank()) {
                Text(uiState.tokenName, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Balance:\n${uiState.balanceText}", style = MaterialTheme.typography.bodyLarge)
            Text("Properties:\n${uiState.propertyCount}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Jail:\n${uiState.jailStatusText}",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text("OWNED PROPERTIES", style = MaterialTheme.typography.titleMedium)
            if (uiState.ownedProperties.isEmpty()) {
                Text("NO PROPERTIES OWNED", style = MaterialTheme.typography.bodyLarge)
            } else {
                uiState.ownedProperties.forEach { property ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onPropertySelected(property.propertyId) }
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = property.propertyName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Property ${property.propertyName}"
                            },
                        )
                        Text(property.colorGroup, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Rent Level:\n${property.rentLevel} / ${property.maxRentLevel}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Current Rent:\n${property.currentRentText}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Purchase Price:\n${property.purchasePriceText}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            when (uiState.step) {
                PlayerDetailsStep.Hub -> {
                    if (uiState.result == null) {
                        Text("BANK ACTIONS", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = viewModel::onCollectGo, modifier = Modifier.fillMaxWidth()) {
                            Text("COLLECT GO")
                        }
                        Button(onClick = viewModel::onLocation, modifier = Modifier.fillMaxWidth()) {
                            Text("LOCATION")
                        }
                        if (uiState.inJail) {
                            Button(onClick = viewModel::onGetOutOfJail, modifier = Modifier.fillMaxWidth()) {
                                Text("GET OUT OF JAIL")
                            }
                        } else {
                            Button(onClick = viewModel::onGoToJail, modifier = Modifier.fillMaxWidth()) {
                                Text("GO TO JAIL")
                            }
                        }
                    }
                }
                PlayerDetailsStep.GoConfirm -> {
                    Text(
                        "Collect ${viewModel.goSalaryText()} for:",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PlayerIdentity(
                        playerId = uiState.playerId,
                        playerName = uiState.playerName,
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Use only when ${uiState.playerName} passed or landed on GO during normal movement.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("COLLECT ${viewModel.goSalaryText()}"),
                        onConfirm = viewModel::onConfirmGo,
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                PlayerDetailsStep.LocationConfirm -> {
                    Text("LOCATION", style = MaterialTheme.typography.titleMedium)
                    PlayerIdentity(
                        playerId = uiState.playerId,
                        playerName = uiState.playerName,
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text("Pay ${viewModel.locationFeeText()}?", style = MaterialTheme.typography.bodyLarge)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.locationFeeText()}"),
                        onConfirm = viewModel::onConfirmLocation,
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                PlayerDetailsStep.GoToJailConfirm -> {
                    PlayerIdentity(
                        playerId = uiState.playerId,
                        playerName = uiState.playerName,
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Send to Jail?\n\nMove the physical token directly to Jail.\n\nDo not collect ${viewModel.goSalaryText()}.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("GO TO JAIL"),
                        onConfirm = viewModel::onConfirmGoToJail,
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                PlayerDetailsStep.JailOptions -> {
                    Text("IN JAIL", style = MaterialTheme.typography.titleMedium)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.jailFeeText()} TO LEAVE JAIL"),
                        onConfirm = viewModel::onPayJailFee,
                        extraActions = listOf(
                            BankingExtraAction(
                                label = "RELEASE AFTER DOUBLES",
                                onClick = viewModel::onJailDoubles,
                                contentDescription = "Release player after rolling doubles",
                            ),
                            BankingExtraAction(
                                label = "RECORD FAILED DOUBLES",
                                onClick = viewModel::onFailedDoublesInfo,
                                contentDescription = "Show failed doubles guidance",
                            ),
                        ),
                        cancelLabel = BankingActionLabels.cancel("BACK"),
                        onCancel = viewModel::onBack,
                    )
                }
                PlayerDetailsStep.JailDoublesConfirm -> {
                    Text("Did the player roll doubles?", style = MaterialTheme.typography.bodyLarge)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("YES — RELEASE"),
                        onConfirm = viewModel::onConfirmJailDoubles,
                        cancelLabel = BankingActionLabels.cancel("NO"),
                        onCancel = viewModel::onBack,
                    )
                }
            }

            uiState.result?.let { result ->
                PlayerDetailsResultContent(result = result, onDone = viewModel::onDone)
            }
        }
    }
}

@Composable
private fun PlayerDetailsResultContent(result: GameplayResultUiModel, onDone: () -> Unit) {
    GameplayResultPresentation(result = result)
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("DONE"),
        onConfirm = onDone,
    )
}
