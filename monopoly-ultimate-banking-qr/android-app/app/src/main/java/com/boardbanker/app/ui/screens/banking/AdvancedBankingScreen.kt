package com.boardbanker.app.ui.screens.banking

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.banking.UndoAuthorizationController
import com.boardbanker.app.banking.UndoAuthorizationPhase
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedBankingScreen(
    viewModel: AdvancedBankingViewModel,
    onNavigateBack: () -> Unit,
    onOpenScanner: (ScanRequest) -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onNavigateToGameStatus: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onContinueLocationOnActiveGame: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.step == AdvancedBankingStep.UndoAuthorization) {
        viewModel.onCancelUndo()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AdvancedBankingEvent.NavigateBack -> onNavigateBack()
                is AdvancedBankingEvent.OpenScanner -> onOpenScanner(event.request)
                AdvancedBankingEvent.NavigateToDebt -> onNavigateToDebt()
                AdvancedBankingEvent.NavigateToGameOver -> onNavigateToGameOver()
                AdvancedBankingEvent.NavigateToGameStatus -> onNavigateToGameStatus()
                AdvancedBankingEvent.NavigateToHistory -> onNavigateToHistory()
                AdvancedBankingEvent.ContinueLocationOnActiveGame -> onContinueLocationOnActiveGame()
            }
        }
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Banking") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BANK ACTIONS") },
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
            when (val step = uiState.step) {
                AdvancedBankingStep.Hub -> {
                    if (uiState.result == null) {
                        Button(onClick = viewModel::onCollectGo, modifier = Modifier.fillMaxWidth()) {
                            Text("COLLECT GO")
                        }
                        Button(onClick = viewModel::onLocation, modifier = Modifier.fillMaxWidth()) {
                            Text("LOCATION")
                        }
                        Button(onClick = viewModel::onGoToJail, modifier = Modifier.fillMaxWidth()) {
                            Text("GO TO JAIL")
                        }
                        Button(onClick = viewModel::onGetOutOfJail, modifier = Modifier.fillMaxWidth()) {
                            Text("GET OUT OF JAIL")
                        }
                        Button(
                            onClick = viewModel::onUndo,
                            enabled = uiState.canUndo,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.canUndo) "UNDO LAST ACTION" else "UNDO LAST ACTION")
                        }
                        if (!uiState.canUndo) {
                            Text(
                                "Nothing can currently be undone.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(onClick = viewModel::onGameStatus, modifier = Modifier.fillMaxWidth()) {
                            Text("GAME STATUS")
                        }
                        Button(onClick = viewModel::onHistory, modifier = Modifier.fillMaxWidth()) {
                            Text("RECENT BANKING")
                        }
                    }
                }
                AdvancedBankingStep.GoScanPlayer -> {
                    Text(
                        "Scan the Player card\nwho passed or landed on GO.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { onOpenScanner(ScanRequest.player()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN PLAYER CARD")
                    }
                }
                is AdvancedBankingStep.GoConfirm -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Collect ${viewModel.goSalaryText()}?\n\n" +
                            "Use this only when the player passed\n" +
                            "or landed on GO during normal movement.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("COLLECT ${viewModel.goSalaryText()}"),
                        onConfirm = { viewModel.onConfirmGo(step.playerId) },
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                AdvancedBankingStep.LocationIntro -> {
                    Text(
                        "LOCATION\n\nPay ${viewModel.locationFeeText()} and move to a Property?",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.locationFeeText()}"),
                        onConfirm = viewModel::onLocationPay,
                        extraActions = listOf(
                            BankingExtraAction(
                                label = "DO NOTHING",
                                onClick = viewModel::onLocationDoNothing,
                                contentDescription = "Do nothing for location",
                            ),
                        ),
                        cancelLabel = BankingActionLabels.cancel("BACK"),
                        onCancel = viewModel::onBack,
                    )
                }
                AdvancedBankingStep.LocationScanPlayer -> {
                    Text("Scan the Player card.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { onOpenScanner(ScanRequest.player()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN PLAYER CARD")
                    }
                }
                is AdvancedBankingStep.LocationConfirmPlayer -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Pay ${viewModel.locationFeeText()}?\n\n" +
                            "Move the physical token to the Property you choose.\n\n" +
                            "Do not collect ${viewModel.goSalaryText()} if you pass GO.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.locationFeeText()}"),
                        onConfirm = { viewModel.onConfirmLocationPlayer(step.playerId) },
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                AdvancedBankingStep.GoToJailScanPlayer -> {
                    Text(
                        "Scan the Player card\nto send to Jail.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { onOpenScanner(ScanRequest.player()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN PLAYER CARD")
                    }
                }
                is AdvancedBankingStep.GoToJailConfirm -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Send to Jail?\n\nMove the physical token\ndirectly to Jail.\n\nDo not collect ${viewModel.goSalaryText()}.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("GO TO JAIL"),
                        onConfirm = { viewModel.onConfirmGoToJail(step.playerId) },
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                AdvancedBankingStep.GetOutOfJailScanPlayer -> {
                    Text(
                        "Scan the jailed Player card.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { onOpenScanner(ScanRequest.player()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN PLAYER CARD")
                    }
                }
                is AdvancedBankingStep.JailOptions -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text("IN JAIL", style = MaterialTheme.typography.titleMedium)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.jailFeeText()} TO LEAVE JAIL"),
                        onConfirm = { viewModel.onPayJailFee(step.playerId) },
                        extraActions = listOf(
                            BankingExtraAction(
                                label = "RELEASE AFTER DOUBLES",
                                onClick = { viewModel.onJailDoubles(step.playerId) },
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
                is AdvancedBankingStep.JailDoublesConfirm -> {
                    Text("Did the player roll doubles?", style = MaterialTheme.typography.bodyLarge)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("YES — RELEASE"),
                        onConfirm = { viewModel.onConfirmJailDoubles(step.playerId) },
                        cancelLabel = BankingActionLabels.cancel("NO"),
                        onCancel = viewModel::onBack,
                    )
                }
                AdvancedBankingStep.UndoAuthorization -> {
                    UndoAuthorizationContent(
                        uiState = uiState,
                        onScan = viewModel::onRequestUndoScan,
                        onCancel = viewModel::onCancelUndo,
                    )
                }
            }

            uiState.result?.let { result ->
                BankingResultContent(result = result, onDone = viewModel::onDone)
            }
        }
    }
}

@Composable
private fun UndoAuthorizationContent(
    uiState: AdvancedBankingUiState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    val authorization = uiState.authorization
    Text(
        UndoAuthorizationController.ALL_PLAYERS_MUST_APPROVE_TITLE,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        UndoAuthorizationController.ALL_PLAYERS_MUST_APPROVE_BODY,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        UndoAuthorizationController.progressLabel(authorization.verifiedCount, authorization.totalCount),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    authorization.players.forEach { player ->
        val waiting = !player.verified
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (player.verified) "✓" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (waiting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            PlayerIdentity(
                playerId = player.playerId,
                playerName = player.displayName,
                iconSize = PlayerIconSize.Small,
            )
            Text(
                if (player.verified) "Verified" else "Waiting to scan",
                style = MaterialTheme.typography.bodyMedium,
                color = if (waiting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    authorization.scanMessage?.takeIf { authorization.phase != UndoAuthorizationPhase.FAILED }?.let { scanMessage ->
        Text(scanMessage, style = MaterialTheme.typography.bodyMedium)
    }
    if (authorization.phase == UndoAuthorizationPhase.FAILED) {
        Text(
            authorization.scanMessage ?: "This action cannot be undone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (authorization.phase == UndoAuthorizationPhase.COLLECTING) {
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("SCAN PLAYER CARD")
        }
    }
    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("CANCEL UNDO")
    }
}

@Composable
private fun BankingResultContent(result: GameplayResultUiModel, onDone: () -> Unit) {
    GameplayResultPresentation(
        result = result,
        showLargePrimaryPlayer = result.title == "WINNER",
    )
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("DONE"),
        onConfirm = onDone,
    )
}
