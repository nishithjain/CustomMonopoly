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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.BackActionButton
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.BankingExtraAction
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.CommonFilledActionButton
import com.boardbanker.app.ui.components.CommonUiIconImage
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.components.DisplayIdentityIcon
import com.boardbanker.app.ui.components.DisplayIdentityTransferRow
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.TopBarIconTitle
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
    val actionAvailability = uiState.actionAvailability

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
                title = {
                    TopBarIconTitle(
                        icon = CommonUiIcon.BANK,
                        title = "Player Details",
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

            if (uiState.step == PlayerDetailsStep.Hub && uiState.result == null) {
                item(key = "back") {
                    BackActionButton(
                        onClick = viewModel::onBack,
                        testTag = "player_details_back",
                    )
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
                    CommonUiIconImage(icon = CommonUiIcon.BANK, contentDescription = "Bank actions")
                    CommonFilledActionButton(
                        icon = CommonUiIcon.COLLECT_GO,
                        label = "Collect GO",
                        onClick = viewModel::onCollectGo,
                        enabled = actionAvailability.collectGoEnabled,
                    )
                    CommonFilledActionButton(
                        icon = CommonUiIcon.LOCATION,
                        label = "Location",
                        onClick = viewModel::onLocation,
                        enabled = actionAvailability.locationEnabled,
                    )
                    if (uiState.inJail) {
                        CommonFilledActionButton(
                            icon = CommonUiIcon.JAIL,
                            label = "Get Out of Jail",
                            onClick = viewModel::onGetOutOfJail,
                            enabled = actionAvailability.getOutOfJailEnabled,
                            modifier = Modifier.semantics {
                                contentDescription = if (actionAvailability.getOutOfJailEnabled) {
                                    "Get Out of Jail"
                                } else {
                                    actionAvailability.actionsDisabledReason ?: "Get Out of Jail disabled"
                                }
                            },
                        )
                    } else {
                        CommonFilledActionButton(
                            icon = CommonUiIcon.JAIL,
                            label = "Go to Jail",
                            onClick = viewModel::onGoToJail,
                            enabled = actionAvailability.goToJailEnabled,
                        )
                    }
                    actionAvailability.actionsDisabledReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_details_actions_disabled_reason")
                                .semantics { contentDescription = reason },
                        )
                    }
                }
            }
            PlayerDetailsStep.GoConfirm -> {
                DisplayIdentityTransferRow(
                    from = DisplayIdentity.Bank,
                    to = DisplayIdentity.Player(uiState.playerId, uiState.playerName),
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
                DisplayIdentityTransferRow(
                    from = DisplayIdentity.Player(uiState.playerId, uiState.playerName),
                    to = DisplayIdentity.Bank,
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
                DisplayIdentityTransferRow(
                    from = DisplayIdentity.Player(uiState.playerId, uiState.playerName),
                    to = DisplayIdentity.Jail,
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
                GetOutOfJailChoiceContent(
                    playerName = uiState.playerName,
                    jailFeeText = viewModel.jailFeeText(),
                    supportsJailPassScan = viewModel.supportsJailPassScan(),
                    actionAvailability = actionAvailability,
                    onPayJailFee = viewModel::onPayJailFee,
                    onScanJailPass = viewModel::onScanJailPass,
                    onReleaseAfterDoubles = viewModel::onReleaseAfterDoubles,
                    onCancel = viewModel::onBack,
                )
            }
        }
    }
}

@Composable
internal fun GetOutOfJailChoiceContent(
    playerName: String,
    jailFeeText: String,
    supportsJailPassScan: Boolean,
    actionAvailability: PlayerDetailsActionAvailability,
    onPayJailFee: () -> Unit,
    onScanJailPass: () -> Unit,
    onReleaseAfterDoubles: () -> Unit,
    onCancel: () -> Unit,
) {
    DisplayIdentityIcon(
        identity = DisplayIdentity.Jail,
        iconSize = PlayerIconSize.Small,
    )
    Text(
        "How would $playerName like to get out of Jail?",
        style = MaterialTheme.typography.bodyLarge,
    )
    if (!supportsJailPassScan) {
        Text(
            "This edition does not include a Get out of Jail Pass Event Card.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    BankingActionBar(
        confirmLabel = "Pay $jailFeeText",
        confirmIcon = CommonUiIcon.MONEY_TRANSFER,
        confirmTestTag = PlayerDetailsTestTags.JAIL_PAY,
        onConfirm = onPayJailFee,
        confirmEnabled = actionAvailability.getOutOfJailEnabled,
        extraActions = buildList {
            if (supportsJailPassScan) {
                add(
                    BankingExtraAction(
                        label = "Scan Get Out of Jail Pass",
                        icon = CommonUiIcon.EVENT_CARD,
                        onClick = onScanJailPass,
                        enabled = actionAvailability.getOutOfJailEnabled,
                        contentDescription = "Scan Get out of Jail Pass Event Card",
                        testTag = PlayerDetailsTestTags.JAIL_SCAN_PASS,
                    ),
                )
            }
            add(
                BankingExtraAction(
                    label = "Release After Doubles",
                    icon = CommonUiIcon.DICE,
                    onClick = onReleaseAfterDoubles,
                    enabled = actionAvailability.getOutOfJailEnabled,
                    contentDescription = "Release player after rolling doubles",
                    testTag = PlayerDetailsTestTags.JAIL_DOUBLES_RELEASE,
                ),
            )
        },
        cancelLabel = BankingActionLabels.cancel("Cancel"),
        cancelTestTag = PlayerDetailsTestTags.JAIL_CANCEL,
        onCancel = onCancel,
        cancelEnabled = true,
    )
}

@Composable
private fun PlayerDetailsResultContent(result: GameplayResultUiModel, onDone: () -> Unit) {
    GameplayResultPresentation(result = result)
    BankingActionBar(
        confirmLabel = BankingActionLabels.confirm("Done"),
        onConfirm = onDone,
    )
}
