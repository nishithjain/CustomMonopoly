package com.boardbanker.app.ui.screens.debt

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.IconLabelRow
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.components.DisplayIdentityRow
import com.boardbanker.app.ui.components.DisplayIdentityTransferRow
import com.boardbanker.app.ui.components.PlayerIconSize

internal object DebtResolutionTestTags {
    const val SETTLEMENT_SUMMARY = "debt_settlement_summary"
    const val AMOUNT_DUE = "debt_amount_due"
    const val SELECTED_PROPERTY_COUNT = "debt_selected_property_count"
    const val SELECTED_PROPERTY_VALUE = "debt_selected_property_value"
    const val REMAINING_DUE = "debt_remaining_due"
    const val CHANGE_AMOUNT = "debt_change_amount"
    const val SETTLEMENT_BUTTON = "debt_settlement_button"
    const val SELECTION_GUIDANCE = "debt_selection_guidance"
    const val BACK_BUTTON = "debt_resolution_back"
    const val NO_ACTIVE_DEBT = "debt_resolution_no_active_debt"

    fun propertyCheckbox(propertyId: String): String = "debt_property_select_$propertyId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtResolutionScreen(
    viewModel: DebtResolutionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGameOver: () -> Unit,
    onOpenPropertyScanner: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { viewModel.onBack() }

    LaunchedEffect(Unit) {
        viewModel.refreshFromSession()
    }

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

    if (uiState.showBankruptcyConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBankruptcyConfirmation,
            title = { Text("Declare Bankruptcy") },
            text = {
                Text(
                    if (uiState.commandInFlight) {
                        "Declaring bankruptcy..."
                    } else {
                        "${uiState.debtorName} cannot pay the remaining ${viewModel.money(uiState.shortfall)}. Continue?"
                    },
                )
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissBankruptcyConfirmation,
                    enabled = !uiState.commandInFlight,
                ) { Text("CANCEL") }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmBankruptcy,
                    enabled = !uiState.commandInFlight,
                ) { Text(if (uiState.commandInFlight) "DECLARING BANKRUPTCY..." else "DECLARE BANKRUPTCY") }
            },
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                uiState.result != null -> {
                    GameplayResultPresentation(result = uiState.result!!)
                    BankingActionBar(
                        confirmLabel = BankingActionLabels.confirm("DONE"),
                        onConfirm = viewModel::onDone,
                    )
                }
                !uiState.hasActiveDebt -> {
                    DebtResolutionNoActiveDebtContent()
                }
                else -> {
                    DebtResolutionActiveContent(
                        uiState = uiState,
                        formatMoney = viewModel::money,
                        onToggleProperty = viewModel::onToggleProperty,
                        onScanPropertyRequested = viewModel::onScanPropertyRequested,
                        onSettleSelected = viewModel::onSettleSelected,
                        onCheckBankruptcy = viewModel::onCheckBankruptcy,
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DebtResolutionTestTags.BACK_BUTTON),
            ) {
                IconLabelRow(
                    icon = CommonUiIcon.BACK,
                    label = "BACK",
                )
            }
        }
    }
}

@Composable
internal fun DebtResolutionNoActiveDebtContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DebtResolutionTestTags.NO_ACTIVE_DEBT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No outstanding debt payment is required.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Return to the game and continue play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DebtResolutionActiveContent(
    uiState: DebtResolutionUiState,
    formatMoney: (Int) -> String,
    onToggleProperty: (String) -> Unit,
    onScanPropertyRequested: () -> Unit,
    onSettleSelected: () -> Unit,
    onCheckBankruptcy: () -> Unit,
) {
    if (uiState.isEventContributorDebt) {
        Text(uiState.eventName, style = MaterialTheme.typography.titleMedium)
        DisplayIdentityTransferRow(
            from = DisplayIdentity.Player(uiState.debtorPlayerId, uiState.debtorName),
            to = DisplayIdentity.Player(uiState.creditorPlayerId, uiState.creditorName),
            iconSize = PlayerIconSize.Normal,
            showFallbackPlayerIcon = true,
        )
        Text(
            buildString {
                append("Amount due: ${formatMoney(uiState.contributionPerPlayer)}\n")
                append("Available cash: ${formatMoney(uiState.availableCash)}\n")
                append("Shortfall: ${formatMoney(uiState.shortfall)}")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = if (uiState.properties.isEmpty()) {
                "No assets are available to sell."
            } else {
                "Select properties to sell to the Bank"
            },
            style = MaterialTheme.typography.titleSmall,
        )
    } else if (uiState.isEventDebt) {
        Text(uiState.eventName, style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append("Contribution per player: ${formatMoney(uiState.contributionPerPlayer)}\n")
                append("Recipients: ${uiState.recipientCount}\n")
                append("Total due: ${formatMoney(uiState.totalEventDue)}\n")
                append("Available cash: ${formatMoney(uiState.availableCash)}\n")
                append("Shortfall: ${formatMoney(uiState.shortfall)}")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        uiState.eventRecipients.forEach { recipient ->
            DisplayIdentityRow(
                identity = DisplayIdentity.Player(recipient.playerId, recipient.playerName),
                iconSize = PlayerIconSize.Compact,
                showFallbackPlayerIcon = true,
            )
        }
        Text(
            text = "Select assets to cover the shortfall",
            style = MaterialTheme.typography.titleSmall,
        )
    } else if (uiState.isEventBankDebit) {
        Text(uiState.eventName, style = MaterialTheme.typography.titleMedium)
        DisplayIdentityTransferRow(
            from = DisplayIdentity.Player(uiState.debtorPlayerId, uiState.debtorName),
            to = DisplayIdentity.Bank,
            iconSize = PlayerIconSize.Normal,
            showFallbackPlayerIcon = true,
        )
        Text(
            buildString {
                append("Amount due: ${formatMoney(uiState.totalEventDue)}\n")
                append("Available cash: ${formatMoney(uiState.availableCash)}\n")
                append("Shortfall: ${formatMoney(uiState.shortfall)}")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Select properties to sell to the Bank",
            style = MaterialTheme.typography.titleSmall,
        )
    } else {
        Text("DEBT PAYMENT", style = MaterialTheme.typography.titleMedium)
        DisplayIdentityTransferRow(
            from = DisplayIdentity.Player(uiState.debtorPlayerId, uiState.debtorName),
            to = if (uiState.creditorIsBank) {
                DisplayIdentity.Bank
            } else {
                DisplayIdentity.Player(uiState.creditorPlayerId, uiState.creditorName)
            },
            iconSize = PlayerIconSize.Normal,
            showFallbackPlayerIcon = true,
        )
        Text(
            buildString {
                append("Amount due:\n${formatMoney(uiState.amountDue)}\n\n")
                append("Available cash:\n${formatMoney(uiState.availableCash)}\n\n")
                append("Remaining after cash:\n${formatMoney(uiState.remainingAfterCash)}")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Select properties to cover",
            style = MaterialTheme.typography.titleSmall,
        )
    }
    DebtSettlementSummaryPanel(
        summary = uiState.settlementSummary,
        formatMoney = formatMoney,
    )
    uiState.properties.forEach { property ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                modifier = Modifier.testTag(DebtResolutionTestTags.propertyCheckbox(property.propertyId)),
                checked = property.propertyId in uiState.selectedPropertyIds,
                onCheckedChange = { onToggleProperty(property.propertyId) },
            )
            Text(
                text = "${property.propertyName}       ${formatMoney(property.debtValue)}",
                modifier = Modifier.semantics {
                    contentDescription = "Property ${property.propertyName}, value ${formatMoney(property.debtValue)}"
                },
            )
        }
    }
    Button(onClick = onScanPropertyRequested, modifier = Modifier.fillMaxWidth()) {
        Text("SCAN PROPERTY TO USE FOR DEBT")
    }
    Button(
        onClick = onSettleSelected,
        enabled = uiState.settlementSummary.isSettleEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DebtResolutionTestTags.SETTLEMENT_BUTTON),
    ) {
        Text(uiState.settlementSummary.settleButtonLabel)
    }
    Button(
        onClick = onCheckBankruptcy,
        enabled = uiState.shortfall > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconLabelRow(icon = CommonUiIcon.DEBT, label = "DECLARE BANKRUPTCY")
    }
}

@Composable
internal fun DebtSettlementSummaryPanel(
    summary: DebtSettlementSummary,
    formatMoney: (Int) -> String,
) {
    val remainingDueColor = if (summary.isDebtFullyCovered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = DebtResolutionTestTags.SETTLEMENT_SUMMARY }
            .testTag(DebtResolutionTestTags.SETTLEMENT_SUMMARY),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Settlement summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SummaryLine(
                label = "Amount due:",
                value = formatMoney(summary.outstandingAmount),
                testTag = DebtResolutionTestTags.AMOUNT_DUE,
            )
            SummaryLine(
                label = "Properties selected:",
                value = summary.propertiesSelectedLabel,
                testTag = DebtResolutionTestTags.SELECTED_PROPERTY_COUNT,
            )
            SummaryLine(
                label = "Selected property value:",
                value = formatMoney(summary.selectedPropertyValue),
                testTag = DebtResolutionTestTags.SELECTED_PROPERTY_VALUE,
            )
            SummaryLine(
                label = "Remaining due:",
                value = formatMoney(summary.remainingDue),
                testTag = DebtResolutionTestTags.REMAINING_DUE,
            )
            if (summary.showChangeRows) {
                SummaryLine(
                    label = "Change returned to ${summary.changeRecipientName}:",
                    value = formatMoney(summary.changeAmount),
                    testTag = DebtResolutionTestTags.CHANGE_AMOUNT,
                )
                summary.changePayerName?.let { payerName ->
                    Text(
                        text = "Change paid by $payerName",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = summary.selectionGuidance,
                style = MaterialTheme.typography.bodyMedium,
                color = remainingDueColor,
                modifier = Modifier.testTag(DebtResolutionTestTags.SELECTION_GUIDANCE),
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    testTag: String,
) {
    Text(
        text = "$label $value",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(testTag),
    )
}
