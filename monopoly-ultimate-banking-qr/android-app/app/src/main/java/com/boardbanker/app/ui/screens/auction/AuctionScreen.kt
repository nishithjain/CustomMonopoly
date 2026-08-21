package com.boardbanker.app.ui.screens.auction

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boardbanker.app.ui.components.BankingActionBar
import com.boardbanker.app.ui.components.BankingActionLabels
import com.boardbanker.app.ui.components.GameplayResultPresentation
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionScreen(
    viewModel: AuctionViewModel,
    onNavigateBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onNavigateToDebt: () -> Unit,
    onNavigateToGameOver: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuctionEvent.NavigateBack -> onNavigateBack()
                AuctionEvent.OpenScanner -> onOpenScanner()
                AuctionEvent.NavigateToDebt -> onNavigateToDebt()
                AuctionEvent.NavigateToGameOver -> onNavigateToGameOver()
            }
        }
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Auction") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }

    if (uiState.showNoBids) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("NO BIDS") },
            text = { Text("Bank keeps the Property unowned.") },
            confirmButton = {
                TextButton(onClick = viewModel::onRestartAuction) { Text("RESTART AUCTION") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onLeaveUnowned) { Text("LEAVE PROPERTY UNOWNED") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AUCTION") }) },
    ) { innerPadding ->
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
                Text(uiState.propertyName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Current Bid:\n${viewModel.money(uiState.currentBid)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Highest Bidder:", style = MaterialTheme.typography.labelMedium)
                val highestBidderName = uiState.highestBidderName
                if (highestBidderName != null) {
                    PlayerIdentity(
                        playerId = uiState.highestBidderId,
                        playerName = highestBidderName,
                        iconSize = PlayerIconSize.Normal,
                    )
                } else {
                    Text("None", style = MaterialTheme.typography.bodyLarge)
                }
                val nextBid = uiState.currentBid + uiState.bidIncrement
                Text(
                    "Next Bid:\n${viewModel.money(nextBid)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Time Remaining:\n${uiState.remainingSeconds}s",
                    style = MaterialTheme.typography.titleMedium,
                )
                BankingActionBar(
                    middleLabel = BankingActionLabels.middle("BID +${viewModel.money(uiState.bidIncrement)}"),
                    onMiddle = viewModel::onBidRequested,
                    middleEnabled = uiState.auctionRunning && !uiState.commandInFlight,
                    cancelLabel = if (uiState.currentBid == 0) {
                        BankingActionLabels.cancel()
                    } else {
                        null
                    },
                    onCancel = if (uiState.currentBid == 0) viewModel::onCancelBeforeFirstBid else null,
                )
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
