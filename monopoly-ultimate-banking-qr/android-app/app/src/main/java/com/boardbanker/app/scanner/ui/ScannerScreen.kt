package com.boardbanker.app.scanner.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boardbanker.app.ui.components.CardFrontImage
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boardbanker.app.BuildConfig
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.scanner.ScannerViewModel
import com.boardbanker.app.scanner.ScannerViewModelFactory
import com.boardbanker.app.scanner.camera.CameraPreview
import com.boardbanker.app.scanner.camera.CameraQrCodeSource
import com.boardbanker.app.scanner.model.CameraPermissionStatus
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.app.scanner.model.ScannerUiState
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.GameDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    scanRequest: ScanRequest = ScanRequest.gameCard(),
    onCardAccepted: ((ResolvedCard) -> Unit)? = null,
    definitions: GameDefinitions? = null,
    viewModelKey: String? = null,
    viewModel: ScannerViewModel = viewModel(
        key = viewModelKey,
        factory = ScannerViewModelFactory(
            application = LocalContext.current.applicationContext as android.app.Application,
            scanRequest = scanRequest,
            definitions = definitions,
        ),
    ),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiModel by viewModel.uiModel.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val cameraSource = remember { CameraQrCodeSource(context) }
    var cardAccepted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permanentlyDenied = !granted &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) &&
            permissionStatus == CameraPermissionStatus.REQUESTING
        viewModel.onPermissionResult(granted = granted, permanentlyDenied = permanentlyDenied)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(granted = true, permanentlyDenied = false)
        }
    }

    LaunchedEffect(permissionStatus, uiModel.state) {
        if (permissionStatus == CameraPermissionStatus.GRANTED &&
            uiModel.state != ScannerUiState.CARD_RESOLVED &&
            uiModel.state != ScannerUiState.UNKNOWN_CARD &&
            uiModel.state != ScannerUiState.WRONG_CARD_TYPE &&
            uiModel.state != ScannerUiState.ERROR
        ) {
            viewModel.attachQrSource(cameraSource)
        }
    }

    LaunchedEffect(uiModel.state) {
        if (uiModel.state == ScannerUiState.CARD_RESOLVED) {
            viewModel.stopCamera()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopCamera()
            cameraSource.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(scanRequest.heading) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiModel.state) {
                ScannerUiState.CAMERA_PERMISSION_REQUIRED,
                ScannerUiState.CAMERA_PERMISSION_DENIED,
                -> PermissionContent(
                    permanentlyDenied = uiModel.permissionPermanentlyDenied,
                    onAllowCamera = {
                        viewModel.requestPermission()
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )

                ScannerUiState.ERROR -> ErrorContent(
                    message = uiModel.errorMessage ?: "Camera error",
                    onRetry = { viewModel.scanAnotherCard() },
                )

                ScannerUiState.CARD_RESOLVED -> {
                    val resolvedCard = uiModel.resolvedCard!!
                    ResolvedContent(
                        card = resolvedCard,
                        editionId = definitions?.editionId,
                        editionName = definitions?.edition?.name,
                        onScanAnother = onCardAccepted?.let { null } ?: viewModel::scanAnotherCard,
                        onAccept = onCardAccepted?.let { accept ->
                            {
                                if (!cardAccepted) {
                                    cardAccepted = true
                                    accept(resolvedCard)
                                }
                            }
                        },
                        acceptLabel = onCardAccepted?.let {
                            when (resolvedCard.cardType) {
                                CardType.USER -> "ADD PLAYER"
                                else -> "ACCEPT CARD"
                            }
                        },
                    )
                }

                ScannerUiState.UNKNOWN_CARD -> UnknownContent(
                    title = "UNKNOWN GAME CARD",
                    debugPayload = if (BuildConfig.DEBUG) uiModel.unknownPayload else null,
                    onScanAgain = { viewModel.scanAnotherCard() },
                )

                ScannerUiState.WRONG_CARD_TYPE -> WrongCardTypeContent(
                    message = uiModel.wrongCardTypeMessage ?: "Wrong card type.",
                    onScanAgain = { viewModel.scanAnotherCard() },
                )

                else -> ScanningContent(
                    cameraSource = cameraSource,
                    onCameraReady = viewModel::onCameraReady,
                    onCameraError = viewModel::onCameraError,
                    statusText = when (uiModel.state) {
                        ScannerUiState.STARTING_CAMERA -> "Starting camera..."
                        ScannerUiState.PROCESSING -> "Processing scan..."
                        else -> scanRequest.overlayInstruction
                    },
                    expectedCardType = scanRequest.singleExpectedType?.let { expectedTypeLabel(it) },
                    editionName = definitions?.edition?.name,
                )
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}

private fun expectedTypeLabel(type: CardType): String = when (type) {
    CardType.USER -> "Expected: Player Card"
    CardType.PROPERTY -> "Expected: Property Card"
    CardType.EVENT -> "Expected: Event Card"
}

@Composable
private fun ScanningContent(
    cameraSource: CameraQrCodeSource,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    statusText: String,
    expectedCardType: String? = null,
    editionName: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        CameraPreview(
            cameraSource = cameraSource,
            onCameraReady = onCameraReady,
            onCameraError = onCameraError,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "QR HERE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Text(
        text = statusText,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    if (expectedCardType != null) {
        Text(
            text = expectedCardType,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (editionName != null) {
        Text(
            text = "Edition: $editionName",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = "Hold card steady",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text("Camera permission is required to scan game cards.")
    if (permanentlyDenied) {
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("OPEN APP SETTINGS")
        }
    } else {
        Button(onClick = onAllowCamera, modifier = Modifier.fillMaxWidth()) {
            Text("ALLOW CAMERA")
        }
    }
}

@Composable
private fun ResolvedContent(
    card: ResolvedCard,
    editionId: String?,
    editionName: String? = null,
    acceptLabel: String? = null,
    onScanAnother: (() -> Unit)?,
    onAccept: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editionId != null) {
            CardFrontImage(
                editionId = editionId,
                cardType = card.cardType,
                cardId = card.cardId,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("CARD RECOGNIZED", style = MaterialTheme.typography.headlineSmall)
        if (editionName != null) {
            Text("Edition: $editionName")
        }
        Text("Type:\n${card.cardType.name}")
        Text("ID:\n${card.cardId}")
        if (card.cardType == CardType.USER) {
            PlayerIdentity(
                playerId = card.cardId,
                playerName = card.displayName,
                iconSize = PlayerIconSize.Large,
                vertical = true,
            )
        } else {
            Text(
                text = "Name:\n${card.displayName}",
                modifier = Modifier.semantics {
                    contentDescription = "${card.cardType.name} ${card.displayName}"
                },
            )
        }
        onAccept?.let { accept ->
            Button(onClick = accept, modifier = Modifier.fillMaxWidth()) {
                Text(acceptLabel ?: "ACCEPT")
            }
        }
        onScanAnother?.let { scanAnother ->
            Button(onClick = scanAnother, modifier = Modifier.fillMaxWidth()) {
                Text("SCAN ANOTHER CARD")
            }
        }
    }
}

@Composable
private fun UnknownContent(
    title: String,
    debugPayload: String?,
    onScanAgain: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text("This QR is not part of the registered game cards.")
    if (debugPayload != null) {
        Text("Debug payload: $debugPayload", style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
        Text("SCAN AGAIN")
    }
}

@Composable
private fun WrongCardTypeContent(
    message: String,
    onScanAgain: () -> Unit,
) {
    Text("Wrong card type", style = MaterialTheme.typography.headlineSmall)
    Text(message, textAlign = TextAlign.Center)
    Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
        Text("SCAN AGAIN")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Text("Scanner error", style = MaterialTheme.typography.headlineSmall)
    Text(message)
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("TRY AGAIN")
    }
}
