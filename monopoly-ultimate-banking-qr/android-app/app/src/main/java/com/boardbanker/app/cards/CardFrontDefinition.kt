package com.boardbanker.app.cards

data class CardFrontDefinition(
    val cardId: String,
    val cardType: String,
    val name: String,
    val sourceFrontPath: String,
    val runtimeAssetPath: String,
    val asset: String,
    val orientation: String,
    val rotationApplied: Boolean,
    val width: Int,
    val height: Int,
)

data class CardFrontImage(
    val cardId: String,
    val assetPath: String,
    val orientation: String,
    val width: Int,
    val height: Int,
)
