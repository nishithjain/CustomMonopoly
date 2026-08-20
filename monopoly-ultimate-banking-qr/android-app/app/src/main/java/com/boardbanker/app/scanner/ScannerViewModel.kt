package com.boardbanker.app.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boardbanker.app.BankingQrApplication
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.ScanAudioFeedback
import com.boardbanker.app.scanner.delivery.ScanDeliveryStage
import com.boardbanker.app.scanner.delivery.ScanDeliveryTrace
import com.boardbanker.app.scanner.delivery.ScanResultDeliverer
import com.boardbanker.app.scanner.model.CameraPermissionStatus
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.app.scanner.model.ScannerUiModel
import com.boardbanker.app.scanner.model.ScannerUiState
import com.boardbanker.core.card.CardResolution
import com.boardbanker.core.card.CardType
import com.boardbanker.core.scanner.ScanProcessorResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerViewModel(
    application: Application,
    private val expectedCardType: CardType? = null,
    private val gameAudioFeedback: GameAudioFeedback? = null,
    private val scanResultDeliverer: ScanResultDeliverer? = null,
) : AndroidViewModel(application) {
    private val app = application as BankingQrApplication
    private val audio: GameAudioFeedback = gameAudioFeedback ?: app.gameAudioFeedback
    private val deliverer: ScanResultDeliverer = scanResultDeliverer ?: app.scanResultDeliverer
    private val controller by lazy {
        ScannerController(app.gameDefinitions)
    }

    private val _uiModel = MutableStateFlow(ScannerUiModel(state = ScannerUiState.CAMERA_PERMISSION_REQUIRED))
    val uiModel: StateFlow<ScannerUiModel> = _uiModel.asStateFlow()

    private val _permissionStatus = MutableStateFlow(CameraPermissionStatus.NOT_REQUESTED)
    val permissionStatus: StateFlow<CameraPermissionStatus> = _permissionStatus.asStateFlow()

    private var activeSource: QrCodeSource? = null
    private var collectionJob: Job? = null

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        when {
            granted -> {
                _permissionStatus.value = CameraPermissionStatus.GRANTED
                _uiModel.update {
                    it.copy(
                        state = ScannerUiState.STARTING_CAMERA,
                        errorMessage = null,
                        permissionPermanentlyDenied = false,
                    )
                }
            }
            permanentlyDenied -> {
                _permissionStatus.value = CameraPermissionStatus.PERMANENTLY_DENIED
                _uiModel.update {
                    it.copy(
                        state = ScannerUiState.CAMERA_PERMISSION_DENIED,
                        permissionPermanentlyDenied = true,
                        errorMessage = "Camera permission is required to scan game cards.",
                    )
                }
            }
            else -> {
                _permissionStatus.value = CameraPermissionStatus.DENIED
                _uiModel.update {
                    it.copy(
                        state = ScannerUiState.CAMERA_PERMISSION_REQUIRED,
                        permissionPermanentlyDenied = false,
                        errorMessage = "Camera permission is required to scan game cards.",
                    )
                }
            }
        }
    }

    fun requestPermission() {
        _permissionStatus.value = CameraPermissionStatus.REQUESTING
    }

    fun attachQrSource(source: QrCodeSource) {
        if (app.definitionsLoadError != null) {
            onCameraError("Failed to load game card data: ${app.definitionsLoadError}")
            return
        }
        if (_permissionStatus.value != CameraPermissionStatus.GRANTED) return
        if (_uiModel.value.state == ScannerUiState.CARD_RESOLVED ||
            _uiModel.value.state == ScannerUiState.UNKNOWN_CARD ||
            _uiModel.value.state == ScannerUiState.WRONG_CARD_TYPE
        ) {
            return
        }

        if (activeSource === source && collectionJob?.isActive == true) {
            return
        }

        stopCamera()
        activeSource = source
        _uiModel.update { it.copy(state = ScannerUiState.SCANNING, errorMessage = null) }

        collectionJob = viewModelScope.launch {
            source.detections.collect { event ->
                when (event) {
                    is QrDetectionEvent.QrDetected -> handlePayload(event.payload)
                    QrDetectionEvent.NoQrDetected -> controller.onNoQrDetected()
                }
            }
        }
        source.start()
    }

    fun onCameraReady() {
        _uiModel.update {
            if (it.state == ScannerUiState.STARTING_CAMERA || it.state == ScannerUiState.SCANNING) {
                it.copy(state = ScannerUiState.SCANNING)
            } else {
                it
            }
        }
    }

    fun onCameraError(message: String) {
        _uiModel.update {
            it.copy(state = ScannerUiState.ERROR, errorMessage = message)
        }
    }

    fun scanAnotherCard() {
        controller.prepareForNextScan()
        _uiModel.update {
            it.copy(
                state = ScannerUiState.SCANNING,
                resolvedCard = null,
                unknownPayload = null,
                wrongCardTypeMessage = null,
                errorMessage = null,
            )
        }
    }

    fun stopCamera() {
        collectionJob?.cancel()
        collectionJob = null
        activeSource?.stop()
        activeSource = null
    }

    override fun onCleared() {
        stopCamera()
        super.onCleared()
    }

    private fun handlePayload(payload: String) {
        if (_uiModel.value.state == ScannerUiState.CARD_RESOLVED ||
            _uiModel.value.state == ScannerUiState.UNKNOWN_CARD ||
            _uiModel.value.state == ScannerUiState.WRONG_CARD_TYPE
        ) {
            return
        }

        val scanAttemptId = deliverer.nextScanAttemptId()
        ScanDeliveryTrace.log(scanAttemptId, ScanDeliveryStage.SCAN_DETECTED)

        _uiModel.update { it.copy(state = ScannerUiState.PROCESSING) }

        when (val result = controller.onQrPayload(payload)) {
            is ScanProcessorResult.Ignored -> {
                _uiModel.update {
                    if (it.state == ScannerUiState.PROCESSING) {
                        it.copy(state = ScannerUiState.SCANNING)
                    } else {
                        it
                    }
                }
            }
            is ScanProcessorResult.CardResolved -> {
                ScanDeliveryTrace.log(
                    scanAttemptId,
                    ScanDeliveryStage.SCAN_GATE_ACCEPTED,
                    "cardId=${result.resolution.cardId}",
                )
                ScanDeliveryTrace.log(
                    scanAttemptId,
                    ScanDeliveryStage.CARD_RESOLVED,
                    "cardId=${result.resolution.cardId}",
                )
                val validation = ScannerCardFilter.validateCardType(result.resolution, expectedCardType)
                val resolvedCard = result.resolution.toResolvedCard()
                when (validation) {
                    CardTypeValidation.Accepted -> {
                        controller.lockAfterResolved()
                        deliverer.stageResolvedCard(scanAttemptId, resolvedCard)
                        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)
                        _uiModel.update {
                            it.copy(
                                state = ScannerUiState.CARD_RESOLVED,
                                resolvedCard = resolvedCard,
                                unknownPayload = null,
                                wrongCardTypeMessage = null,
                            )
                        }
                    }
                    is CardTypeValidation.WrongType -> {
                        controller.lockAfterResolved()
                        ScanAudioFeedback.onScanProcessed(audio, result, validation, scanAttemptId)
                        _uiModel.update {
                            it.copy(
                                state = ScannerUiState.WRONG_CARD_TYPE,
                                resolvedCard = null,
                                unknownPayload = null,
                                wrongCardTypeMessage = wrongCardTypeMessage(validation.expected),
                            )
                        }
                    }
                }
            }
            is ScanProcessorResult.UnknownCard -> {
                ScanDeliveryTrace.log(scanAttemptId, ScanDeliveryStage.SCAN_GATE_ACCEPTED, "unknown")
                controller.lockAfterResolved()
                ScanAudioFeedback.onScanProcessed(audio, result, validation = null, scanAttemptId)
                _uiModel.update {
                    it.copy(
                        state = ScannerUiState.UNKNOWN_CARD,
                        resolvedCard = null,
                        unknownPayload = result.qrPayload,
                        wrongCardTypeMessage = null,
                    )
                }
            }
        }
    }

    private fun wrongCardTypeMessage(expected: CardType): String = when (expected) {
        CardType.USER -> "PLAYER CARD EXPECTED\n\nPlease scan a Player card."
        CardType.PROPERTY -> "PROPERTY CARD EXPECTED\n\nPlease scan a Property card."
        CardType.EVENT -> "EVENT CARD EXPECTED\n\nPlease scan an Event card."
    }

    private fun CardResolution.Success.toResolvedCard() = ResolvedCard(
        cardId = cardId,
        cardType = cardType,
        displayName = displayName,
        qrPayload = qrPayload,
    )
}
