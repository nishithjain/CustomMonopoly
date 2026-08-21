package com.boardbanker.app.ui.screens.game

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateHome: () -> Unit,
    onOpenScanner: (CardType?) -> Unit,
    onNavigateToBanking: () -> Unit,
    onNavigateToAuction: (String, String) -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onNavigateToPlayerDetails: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GameEvent.NavigateHome -> onNavigateHome()
                is GameEvent.OpenScanner -> onOpenScanner(event.expectedCardType)
                GameEvent.NavigateToBanking -> onNavigateToBanking()
                is GameEvent.NavigateToAuction -> onNavigateToAuction(event.propertyId, event.startedByPlayerId)
                GameEvent.NavigateToDebt -> onNavigateToDebt()
                GameEvent.NavigateToGameOver -> onNavigateToGameOver()
                is GameEvent.NavigateToPlayerDetails -> onNavigateToPlayerDetails(event.playerId)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resumeLocationWorkflowIfPending()
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Gameplay") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text("OK") }
            },
        )
    }

    if (uiState.showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAbandonConfirm,
            title = { Text("ABANDON CURRENT GAME?") },
            text = { Text("This will remove the current saved active game.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAbandonGame) { Text("ABANDON GAME") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAbandonConfirm) { Text("KEEP PLAYING") }
            },
        )
    }

    val displayCardId = ActiveGameCardUiPolicy.displayCardId(uiState.workflowState, uiState.result)
    val actionVisibility = ActiveGameCardUiPolicy.actionVisibility(
        workflowState = uiState.workflowState,
        result = uiState.result,
        gameplayLocked = uiState.gameplayLocked,
    )
    val showCardInteraction = ActiveGameCardUiPolicy.showCardInteraction(
        workflowState = uiState.workflowState,
        result = uiState.result,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("ACTIVE GAME") }) },
    ) { innerPadding ->
        if (uiState.loading) {
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!showCardInteraction) {
                item {
                    uiState.players.forEach { player ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onPlayerSelected(player.playerId) }
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PlayerIdentity(
                                playerId = player.playerId,
                                playerName = player.playerName,
                                iconSize = PlayerIconSize.Normal,
                            )
                            Text(player.balanceText, style = MaterialTheme.typography.bodyLarge)
                            Text(player.summaryLine, style = MaterialTheme.typography.bodyMedium)
                            if (player.inJail) {
                                Text("IN JAIL", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            uiState.activeEventMessage?.let { message ->
                item {
                    Text(
                        "ACTIVE EVENT\n\n$message",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (showCardInteraction && displayCardId != null) {
                item {
                    CardFrontImage(cardId = displayCardId)
                }
            }

            uiState.cardPresentation?.let { presentation ->
                item {
                            presentation.cardTypeLabel.let {
                                Text(it, style = MaterialTheme.typography.labelLarge)
                            }
                    Text(presentation.title, style = MaterialTheme.typography.headlineSmall)
                    if (presentation.ownerPlayerId != null && presentation.ownerName != null) {
                        Text("Owner:", style = MaterialTheme.typography.labelMedium)
                        PlayerIdentity(
                            playerId = presentation.ownerPlayerId,
                            playerName = presentation.ownerName ?: "",
                            iconSize = PlayerIconSize.Normal,
                        )
                    }
                    Text(presentation.body, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                when (val workflow = uiState.workflowState) {
                    GameplayWorkflowState.Ready -> {
                        if (uiState.result == null && !uiState.gameplayLocked) {
                            Button(
                                onClick = viewModel::onScanCardRequested,
                                enabled = !uiState.commandInFlight,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("SCAN CARD")
                            }
                            Button(
                                onClick = viewModel::onBankActionsRequested,
                                enabled = !uiState.commandInFlight,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("BANK ACTIONS")
                            }
                        } else if (uiState.gameplayLocked) {
                            Text(
                                "Game finished. Normal gameplay is disabled.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    is GameplayWorkflowState.UnownedPropertyDecision -> {
                        if (actionVisibility.showBuy || actionVisibility.showAuction) {
                            UnownedPropertyContent(
                                purchasePrice = uiState.cardPresentation?.buyAmount,
                                onBuy = viewModel::onBuyProperty,
                                onAuction = viewModel::onAuctionProperty,
                                onCancel = viewModel::onCancelWorkflow,
                                commandInFlight = uiState.commandInFlight,
                                money = viewModel::money,
                            )
                        }
                    }
                    is GameplayWorkflowState.WaitingForPurchasingPlayer,
                    is GameplayWorkflowState.WaitingForAuctionStarter,
                    -> ScanPromptContent(
                        prompt = uiState.scanPrompt ?: "Scan the required Player card.",
                        scanButtonLabel = "SCAN CARD",
                        onScan = viewModel::onScanRequested,
                        onCancel = viewModel::onCancelWorkflow,
                        commandInFlight = uiState.commandInFlight,
                        showCancel = actionVisibility.showCancel,
                    )
                    is GameplayWorkflowState.WaitingForRentPayer -> {
                        if (actionVisibility.showScanPlayer) {
                            ScanPromptContent(
                                prompt = uiState.scanPrompt ?: "Scan the Player who landed here.",
                                scanButtonLabel = "SCAN PLAYER",
                                onScan = viewModel::onScanRequested,
                                onCancel = viewModel::onCancelWorkflow,
                                commandInFlight = uiState.commandInFlight,
                                showCancel = actionVisibility.showCancel,
                            )
                        }
                    }
                    is GameplayWorkflowState.EventIntro -> {
                        if (actionVisibility.showContinue) {
                            EventContinueContent(
                                onContinue = viewModel::onEventContinue,
                                onCancel = viewModel::onCancelWorkflow,
                                commandInFlight = uiState.commandInFlight,
                            )
                        }
                    }
                    is GameplayWorkflowState.EventCollectingTargets -> ScanPromptContent(
                        prompt = uiState.scanPrompt ?: "Scan the required card.",
                        scanButtonLabel = "SCAN CARD",
                        onScan = viewModel::onScanRequested,
                        onCancel = viewModel::onCancelWorkflow,
                        commandInFlight = uiState.commandInFlight,
                        showCancel = actionVisibility.showCancel,
                    )
                    is GameplayWorkflowState.EventConfirm -> EventConfirmContent(
                        onConfirm = viewModel::onEventConfirm,
                        onCancel = viewModel::onCancelWorkflow,
                        commandInFlight = uiState.commandInFlight,
                    )
                    is GameplayWorkflowState.EventPropertyChoice -> EventChoiceContent(
                        onBuy = { viewModel.onEventChoice(GameCommand.EventPropertyChoiceType.BUY) },
                        onAuction = { viewModel.onEventChoice(GameCommand.EventPropertyChoiceType.AUCTION) },
                        onRaise = { viewModel.onEventChoice(GameCommand.EventPropertyChoiceType.RAISE_RENT_LEVEL) },
                        onCancel = viewModel::onCancelWorkflow,
                        commandInFlight = uiState.commandInFlight,
                    )
                    is GameplayWorkflowState.LocationWaitingForDestinationProperty -> {
                        Text("LOCATION", style = MaterialTheme.typography.titleMedium)
                        PlayerIdentity(
                            playerId = workflow.playerId,
                            playerName = viewModel.playerDisplayName(workflow.playerId),
                            iconSize = PlayerIconSize.Normal,
                        )
                        Text(
                            buildString {
                                append("${viewModel.playerDisplayName(workflow.playerId)} paid ${viewModel.locationFeeText()}.\n\n")
                                append("Move ${viewModel.playerDisplayName(workflow.playerId)}'s physical token\n")
                                append("to the Property you choose.\n\n")
                                append("Do NOT collect ${viewModel.goSalaryText()} if you pass GO.\n\n")
                                append("Now scan the Property card.")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        ScanPromptContent(
                            prompt = uiState.scanPrompt ?: "Scan the Property card you moved to.",
                            scanButtonLabel = "SCAN PROPERTY",
                            onScan = viewModel::onScanPropertyRequested,
                            onCancel = viewModel::onCancelWorkflow,
                            commandInFlight = uiState.commandInFlight,
                            showCancel = actionVisibility.showCancel,
                        )
                    }
                    is GameplayWorkflowState.PlayerInfo -> Unit
                    is GameplayWorkflowState.Error -> {
                        BankingActionBar(
                            confirmLabel = BankingActionLabels.confirm("DONE"),
                            onConfirm = viewModel::onDone,
                        )
                    }
                    else -> Unit
                }
            }

            uiState.result?.let { result ->
                item {
                    ResultContent(
                        result = result,
                        onDone = viewModel::onDone,
                        showDone = actionVisibility.showDone,
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::requestAbandonGame,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("END / ABANDON GAME")
                }
            }
        }
    }
}

@Composable
private fun UnownedPropertyContent(
    purchasePrice: Int?,
    onBuy: () -> Unit,
    onAuction: () -> Unit,
    onCancel: () -> Unit,
    commandInFlight: Boolean,
    money: (Int) -> String,
) {
    val buyLabel = purchasePrice?.let { BankingActionLabels.confirm("BUY ${money(it)}") }
        ?: BankingActionLabels.confirm("BUY")
    BankingActionBar(
        confirmLabel = buyLabel,
        onConfirm = onBuy,
        confirmEnabled = !commandInFlight,
        extraActions = listOf(
            BankingExtraAction(
                label = "AUCTION",
                onClick = onAuction,
                enabled = !commandInFlight,
                contentDescription = "Start auction for this property",
            ),
        ),
        cancelLabel = BankingActionLabels.cancel(),
        onCancel = onCancel,
    )
}

@Composable
private fun ScanPromptContent(
    prompt: String,
    scanButtonLabel: String,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    commandInFlight: Boolean,
    showCancel: Boolean,
) {
    Text(prompt, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Button(onClick = onScan, enabled = !commandInFlight, modifier = Modifier.fillMaxWidth()) {
        Text(scanButtonLabel)
    }
    if (showCancel) {
        BankingActionBar(
            cancelLabel = BankingActionLabels.cancel(),
            onCancel = onCancel,
        )
    }
}

@Composable
private fun EventContinueContent(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    commandInFlight: Boolean,
) {
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("CONTINUE"),
        onConfirm = onContinue,
        confirmEnabled = !commandInFlight,
        cancelLabel = BankingActionLabels.cancel(),
        onCancel = onCancel,
    )
}

@Composable
private fun EventConfirmContent(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    commandInFlight: Boolean,
) {
    Text("Apply this Event?", style = MaterialTheme.typography.titleMedium)
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("APPLY EVENT"),
        onConfirm = onConfirm,
        confirmEnabled = !commandInFlight,
        cancelLabel = BankingActionLabels.cancel(),
        onCancel = onCancel,
    )
}

@Composable
private fun EventChoiceContent(
    onBuy: () -> Unit,
    onAuction: () -> Unit,
    onRaise: () -> Unit,
    onCancel: () -> Unit,
    commandInFlight: Boolean,
) {
    Text("Choose an action for this property:", style = MaterialTheme.typography.bodyLarge)
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("BUY"),
        onConfirm = onBuy,
        confirmEnabled = !commandInFlight,
        extraActions = listOf(
            BankingExtraAction(
                label = "RAISE RENT LEVEL",
                onClick = onRaise,
                enabled = !commandInFlight,
                contentDescription = "Raise rent level for this property",
            ),
            BankingExtraAction(
                label = "AUCTION",
                onClick = onAuction,
                enabled = !commandInFlight,
                contentDescription = "Start auction for this property",
            ),
        ),
        cancelLabel = BankingActionLabels.cancel(),
        onCancel = onCancel,
    )
}

@Composable
private fun ResultContent(
    result: GameplayResultUiModel,
    onDone: () -> Unit,
    showDone: Boolean,
) {
    GameplayResultPresentation(
        result = result,
        showLargePrimaryPlayer = result.title == "PLAYER" || result.title == "WINNER",
    )
    if (showDone) {
        BankingActionBar(
            confirmLabel = BankingActionLabels.confirm("DONE"),
            onConfirm = onDone,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
