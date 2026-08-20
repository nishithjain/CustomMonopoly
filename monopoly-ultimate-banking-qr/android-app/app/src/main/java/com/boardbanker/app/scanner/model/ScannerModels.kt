package com.boardbanker.app.scanner.model

import com.boardbanker.core.card.CardType

data class ResolvedCard(
    val cardId: String,
    val cardType: CardType,
    val displayName: String,
    val qrPayload: String,
)

enum class CameraPermissionStatus {
    NOT_REQUESTED,
    REQUESTING,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}

enum class ScannerUiState {
    IDLE,
    STARTING_CAMERA,
    SCANNING,
    PROCESSING,
    CARD_RESOLVED,
    UNKNOWN_CARD,
    WRONG_CARD_TYPE,
    CAMERA_PERMISSION_REQUIRED,
    CAMERA_PERMISSION_DENIED,
    ERROR,
}

data class ScannerUiModel(
    val state: ScannerUiState = ScannerUiState.CAMERA_PERMISSION_REQUIRED,
    val resolvedCard: ResolvedCard? = null,
    val unknownPayload: String? = null,
    val wrongCardTypeMessage: String? = null,
    val errorMessage: String? = null,
    val permissionPermanentlyDenied: Boolean = false,
)
