package com.boardbanker.app.ui.screens.playerdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.core.card.CardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsScreen(
    viewModel: PlayerDetailsViewModel,
    onNavigateBack: () -> Unit,
    onOpenPropertyScanner: () -> Unit,
    onOpenJailPassScanner: (ScanRequest) -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onContinueLocationOnActiveGame: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionAvailability = PlayerDetailsActionAvailability.forPlayer(
        inJail = uiState.inJail,
        commandInFlight = uiState.commandInFlight,
        step = uiState.step,
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlayerDetailsEvent.NavigateBack -> onNavigateBack()
                PlayerDetailsEvent.OpenPropertyScanner -> onOpenPropertyScanner()
                is PlayerDetailsEvent.OpenJailPassScanner -> onOpenJailPassScanner(event.request)
                PlayerDetailsEvent.NavigateToDebt -> onNavigateToDebt()
                PlayerDetailsEvent.NavigateToGameOver -> onNavigateToGameOver()
                PlayerDetailsEvent.ContinueLocationOnActiveGame -> onContinueLocationOnActiveGame()
            }
        }
    }

    uiState.selectedPropertyId?.let { propertyId ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPropertyPreview,
            title = { Text("Property") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardFrontImage(
                        editionId = uiState.editionId,
                        cardType = CardType.PROPERTY,
                        cardId = propertyId,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPropertyPreview) { Text("Close") }
            },
        )
    }

    uiState.selectedEnergyGridId?.let { energyGridId ->
        AlertDialog(
            onDismissRequest = viewModel::dismissEnergyGridPreview,
            title = { Text("Energy Grid") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardFrontImage(
                        editionId = uiState.editionId,
                        cardType = CardType.ENERGY_GRID,
                        cardId = energyGridId,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissEnergyGridPreview) { Text("Close") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Details") },
                navigationIcon = {
                    TextButton(onClick = viewModel::onBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.commandInFlight) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "assets") {
                PlayerDetailsAssetsContent(
                    uiState = uiState,
                    onPropertySelected = viewModel::onPropertySelected,
                    onEnergyGridSelected = viewModel::onEnergyGridSelected,
                )
            }

            item(key = "bank-actions") {
                PlayerDetailsBankActions(
                    uiState = uiState,
                    viewModel = viewModel,
                    actionAvailability = actionAvailability,
                )
            }

            uiState.result?.let { result ->
                item(key = "result") {
                    PlayerDetailsResultContent(result = result, onDone = viewModel::onDone)
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailsBankActions(
    uiState: PlayerDetailsUiState,
    viewModel: PlayerDetailsViewModel,
    actionAvailability: PlayerDetailsActionAvailability,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (uiState.step) {
            PlayerDetailsStep.Hub -> {
                if (uiState.result == null) {
                    Text("Bank Actions", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = viewModel::onCollectGo,
                        enabled = actionAvailability.collectGoEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Collect GO")
                    }
                    Button(
                        onClick = viewModel::onLocation,
                        enabled = actionAvailability.locationEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Location")
                    }
                    if (uiState.inJail) {
                        Button(
                            onClick = viewModel::onGetOutOfJail,
                            enabled = actionAvailability.getOutOfJailEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Get Out of Jail")
                        }
                    } else {
                        Button(
                            onClick = viewModel::onGoToJail,
                            enabled = actionAvailability.goToJailEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Go to Jail")
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
                    confirmLabel = BankingActionLabels.confirm("Collect ${viewModel.goSalaryText()}"),
                    onConfirm = viewModel::onConfirmGo,
                    cancelLabel = BankingActionLabels.cancel(),
                    onCancel = viewModel::onBack,
                )
            }
            PlayerDetailsStep.LocationConfirm -> {
                Text("Location", style = MaterialTheme.typography.titleMedium)
                PlayerIdentity(
                    playerId = uiState.playerId,
                    playerName = uiState.playerName,
                    iconSize = PlayerIconSize.Normal,
                )
                Text("Pay ${viewModel.locationFeeText()}?", style = MaterialTheme.typography.bodyLarge)
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("Pay ${viewModel.locationFeeText()}"),
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
                    confirmLabel = BankingActionLabels.confirm("Go to Jail"),
                    onConfirm = viewModel::onConfirmGoToJail,
                    cancelLabel = BankingActionLabels.cancel(),
                    onCancel = viewModel::onBack,
                )
            }
            PlayerDetailsStep.GetOutOfJailChoice -> {
                Text("Get Out of Jail", style = MaterialTheme.typography.titleMedium)
                Text(
                    "How would ${uiState.playerName} like to get out of Jail?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!viewModel.supportsJailPassScan()) {
                    Text(
                        "This edition does not include a Get out of Jail Pass Event Card.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val jailPassLabel = viewModel.jailPassActionLabel()
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("Pay ${viewModel.jailFeeText()}"),
                    onConfirm = viewModel::onPayJailFee,
                    extraActions = buildList {
                        if (viewModel.supportsJailPassScan()) {
                            add(
                                BankingExtraAction(
                                    label = "Scan Get Out of Jail Pass",
                                    onClick = viewModel::onScanJailPass,
                                    contentDescription = "Scan Get out of Jail Pass Event Card",
                                ),
                            )
                        }
                        if (jailPassLabel != null) {
                            add(
                                BankingExtraAction(
                                    label = jailPassLabel,
                                    onClick = viewModel::onUseJailPass,
                                    contentDescription = "Use stored Get Out of Jail pass",
                                ),
                            )
                        }
                        add(
                            BankingExtraAction(
                                label = "More Options",
                                onClick = viewModel::onOpenJailOptions,
                                contentDescription = "Show additional jail release options",
                            ),
                        )
                    },
                    cancelLabel = BankingActionLabels.cancel("Cancel"),
                    onCancel = viewModel::onBack,
                )
            }
            PlayerDetailsStep.JailOptions -> {
                Text("In Jail", style = MaterialTheme.typography.titleMedium)
                val jailPassLabel = viewModel.jailPassActionLabel()
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("Pay ${viewModel.jailFeeText()} to Leave Jail"),
                    onConfirm = viewModel::onPayJailFee,
                    extraActions = buildList {
                        if (jailPassLabel != null) {
                            add(
                                BankingExtraAction(
                                    label = jailPassLabel,
                                    onClick = viewModel::onUseJailPass,
                                    contentDescription = "Use Get Out of Jail pass",
                                ),
                            )
                        }
                        add(
                            BankingExtraAction(
                                label = "Release After Doubles",
                                onClick = viewModel::onJailDoubles,
                                contentDescription = "Release player after rolling doubles",
                            ),
                        )
                        add(
                            BankingExtraAction(
                                label = "Record Failed Doubles",
                                onClick = viewModel::onFailedDoublesInfo,
                                contentDescription = "Show failed doubles guidance",
                            ),
                        )
                    },
                    cancelLabel = BankingActionLabels.cancel("Back"),
                    onCancel = viewModel::onBack,
                )
            }
            PlayerDetailsStep.JailDoublesConfirm -> {
                Text("Did the player roll doubles?", style = MaterialTheme.typography.bodyLarge)
                BankingActionBar(
                    confirmLabel = BankingActionLabels.confirm("Yes — Release"),
                    onConfirm = viewModel::onConfirmJailDoubles,
                    cancelLabel = BankingActionLabels.cancel("No"),
                    onCancel = viewModel::onBack,
                )
            }
        }
    }
}

@Composable
private fun PlayerDetailsResultContent(result: GameplayResultUiModel, onDone: () -> Unit) {
    GameplayResultPresentation(result = result)
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("Done"),
        onConfirm = onDone,
    )
}
