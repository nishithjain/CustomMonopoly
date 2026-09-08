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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.banking.UndoAuthorizationController
import com.boardbanker.app.banking.UndoAuthorizationPhase
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.BackActionButton
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.CommonFilledActionButton
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.components.DisplayIdentityIcon
import com.boardbanker.app.ui.components.DisplayIdentityTransferRow
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.IconLabelRow
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.TopBarIconTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedBankingScreen(
    viewModel: AdvancedBankingViewModel,
    onNavigateBack: () -> Unit,
    onOpenScanner: (ScanRequest, onCancelled: (() -> Unit)?) -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onNavigateToGameStatus: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onContinueLocationOnActiveGame: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { viewModel.onBack() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AdvancedBankingEvent.NavigateBack -> onNavigateBack()
                is AdvancedBankingEvent.OpenScanner -> onOpenScanner(event.request, event.onCancelled)
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
                title = {
                    TopBarIconTitle(
                        icon = CommonUiIcon.BANK,
                        title = "BANK ACTIONS",
                    )
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
                        val eligibility = uiState.hubEligibility
                        eligibility.activePlayerName?.let { activeName ->
                            Text(
                                "Active player: $activeName",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        CommonFilledActionButton(
                            icon = CommonUiIcon.COLLECT_GO,
                            label = "COLLECT GO",
                            onClick = viewModel::onCollectGo,
                            enabled = eligibility.collectGoEnabled,
                        )
                        CommonFilledActionButton(
                            icon = CommonUiIcon.LOCATION,
                            label = "LOCATION",
                            onClick = viewModel::onLocation,
                            enabled = eligibility.locationEnabled,
                        )
                        CommonFilledActionButton(
                            icon = CommonUiIcon.JAIL,
                            label = "GO TO JAIL",
                            onClick = viewModel::onGoToJail,
                            enabled = eligibility.goToJailEnabled,
                        )
                        CommonFilledActionButton(
                            icon = CommonUiIcon.JAIL,
                            label = "GET OUT OF JAIL",
                            onClick = viewModel::onGetOutOfJail,
                            enabled = eligibility.getOutOfJailEnabled,
                        )
                        if (eligibility.activePlayerInJail) {
                            Text(
                                "Collect GO and Location are unavailable while the active player is in Jail.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (!eligibility.getOutOfJailEnabled) {
                            Text(
                                "Get out of Jail is available only when the active player is in Jail.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        CommonFilledActionButton(
                            icon = CommonUiIcon.UNDO_LAST_ACTION,
                            label = "UNDO LAST ACTION",
                            onClick = viewModel::onUndo,
                            enabled = uiState.canUndo,
                        )
                        if (!uiState.canUndo) {
                            Text(
                                "Nothing can currently be undone.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        CommonFilledActionButton(
                            icon = CommonUiIcon.GAME_STATUS,
                            label = "GAME STATUS",
                            onClick = viewModel::onGameStatus,
                        )
                        CommonFilledActionButton(
                            icon = CommonUiIcon.RECENT_BANKING,
                            label = "RECENT BANKING",
                            onClick = viewModel::onHistory,
                        )
                    }
                }
                is AdvancedBankingStep.GoConfirm -> {
                    DisplayIdentityTransferRow(
                        from = DisplayIdentity.Bank,
                        to = DisplayIdentity.Player(step.playerId, viewModel.playerDisplayName(step.playerId)),
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Collect ${viewModel.goSalaryText()} for ${viewModel.playerDisplayName(step.playerId)}?\n\n" +
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
                    val playerName = uiState.hubEligibility.activePlayerName ?: "the active player"
                    Text(
                        "LOCATION\n\nPay ${viewModel.locationFeeText()} and move $playerName to a Property?",
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
                                icon = CommonUiIcon.DO_NOTHING,
                            ),
                        ),
                        cancelLabel = BankingActionLabels.cancel("BACK"),
                        onCancel = viewModel::onBack,
                    )
                }
                is AdvancedBankingStep.LocationConfirmPlayer -> {
                    DisplayIdentityTransferRow(
                        from = DisplayIdentity.Player(step.playerId, viewModel.playerDisplayName(step.playerId)),
                        to = DisplayIdentity.Bank,
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Pay ${viewModel.locationFeeText()} for ${viewModel.playerDisplayName(step.playerId)}?\n\n" +
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
                is AdvancedBankingStep.GoToJailConfirm -> {
                    DisplayIdentityTransferRow(
                        from = DisplayIdentity.Player(step.playerId, viewModel.playerDisplayName(step.playerId)),
                        to = DisplayIdentity.Jail,
                        iconSize = PlayerIconSize.Normal,
                    )
                    Text(
                        "Send ${viewModel.playerDisplayName(step.playerId)} to Jail?\n\n" +
                            "Move the physical token\ndirectly to Jail.\n\n" +
                            "Do not collect ${viewModel.goSalaryText()}.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("GO TO JAIL"),
                        onConfirm = { viewModel.onConfirmGoToJail(step.playerId) },
                        cancelLabel = BankingActionLabels.cancel(),
                        onCancel = viewModel::onBack,
                    )
                }
                is AdvancedBankingStep.GetOutOfJailChoice -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    DisplayIdentityIcon(
                        identity = DisplayIdentity.Jail,
                        iconSize = PlayerIconSize.Small,
                    )
                    Text(
                        "How would ${viewModel.playerDisplayName(step.playerId)} like to get out of Jail?",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!viewModel.supportsJailPassScan()) {
                        Text(
                            "This edition does not include a Get out of Jail Pass Event Card.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val jailPassLabel = viewModel.jailPassActionLabel(step.playerId)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.jailFeeText()}"),
                        onConfirm = { viewModel.onPayJailFee(step.playerId) },
                        extraActions = buildList {
                            if (viewModel.supportsJailPassScan()) {
                                add(
                                    BankingExtraAction(
                                        label = "SCAN GET OUT OF JAIL PASS",
                                        onClick = { viewModel.onScanJailPass(step.playerId) },
                                        contentDescription = "Scan Get out of Jail Pass Event Card",
                                        icon = CommonUiIcon.SCAN_CARD,
                                    ),
                                )
                            }
                            if (jailPassLabel != null) {
                                add(
                                    BankingExtraAction(
                                        label = jailPassLabel.uppercase(),
                                        onClick = { viewModel.onUseJailPass(step.playerId) },
                                        contentDescription = "Use stored Get Out of Jail pass",
                                    ),
                                )
                            }
                            add(
                                BankingExtraAction(
                                    label = "MORE OPTIONS",
                                    onClick = { viewModel.onOpenJailOptions(step.playerId) },
                                    contentDescription = "Show additional jail release options",
                                ),
                            )
                        },
                        cancelLabel = BankingActionLabels.cancel("CANCEL"),
                        onCancel = viewModel::onBack,
                    )
                }
                is AdvancedBankingStep.JailOptions -> {
                    PlayerIdentity(
                        playerId = step.playerId,
                        playerName = viewModel.playerDisplayName(step.playerId),
                        iconSize = PlayerIconSize.Normal,
                    )
                    DisplayIdentityIcon(
                        identity = DisplayIdentity.Jail,
                        iconSize = PlayerIconSize.Small,
                    )
                    val jailPassLabel = viewModel.jailPassActionLabel(step.playerId)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("PAY ${viewModel.jailFeeText()} TO LEAVE JAIL"),
                        onConfirm = { viewModel.onPayJailFee(step.playerId) },
                        extraActions = buildList {
                            if (jailPassLabel != null) {
                                add(
                                    BankingExtraAction(
                                        label = jailPassLabel,
                                        onClick = { viewModel.onUseJailPass(step.playerId) },
                                        contentDescription = "Use Get Out of Jail pass",
                                    ),
                                )
                            }
                            add(
                                BankingExtraAction(
                                    label = "RELEASE AFTER DOUBLES",
                                    onClick = { viewModel.onJailDoubles(step.playerId) },
                                    contentDescription = "Release player after rolling doubles",
                                ),
                            )
                            add(
                                BankingExtraAction(
                                    label = "RECORD FAILED DOUBLES",
                                    onClick = viewModel::onFailedDoublesInfo,
                                    contentDescription = "Show failed doubles guidance",
                                ),
                            )
                        },
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

            if (uiState.step == AdvancedBankingStep.Hub) {
                BackActionButton(
                    onClick = viewModel::onBack,
                    testTag = "bank_actions_back",
                )
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
        CommonFilledActionButton(
            icon = CommonUiIcon.SCAN_CARD,
            label = "SCAN PLAYER CARD",
            onClick = onScan,
        )
    }
    CommonFilledActionButton(
        icon = CommonUiIcon.CANCEL,
        label = "CANCEL UNDO",
        onClick = onCancel,
    )
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
