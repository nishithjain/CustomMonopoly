package com.boardbanker.app.cards

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CardFrontRegistry private constructor(
    private val byCardId: Map<String, CardFrontDefinition>,
) {
    fun getDefinition(cardId: String): CardFrontDefinition? = byCardId[cardId]

    fun getFrontImage(cardId: String): CardFrontImage? {
        val definition = byCardId[cardId] ?: return null
        return CardFrontImage(
            cardId = definition.cardId,
            assetPath = definition.asset,
            orientation = definition.orientation,
            width = definition.width,
            height = definition.height,
        )
    }

    companion object {
        private const val MANIFEST_ASSET = "cards/android_card_front_manifest.json"

        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun load(context: Context): CardFrontRegistry {
            val payload = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString(ManifestPayload.serializer(), payload)
            val definitions = manifest.cards.mapValues { (_, entry) ->
                CardFrontDefinition(
                    cardId = entry.cardId,
                    cardType = entry.cardType,
                    name = entry.name,
                    sourceFrontPath = entry.sourceFrontPath,
                    runtimeAssetPath = entry.runtimeAssetPath,
                    asset = entry.asset,
                    orientation = entry.orientation,
                    rotationApplied = entry.rotationApplied,
                    width = entry.width,
                    height = entry.height,
                )
            }
            return CardFrontRegistry(definitions)
        }
    }
}

@Serializable
private data class ManifestPayload(
    val schemaVersion: Int = 1,
    val cards: Map<String, ManifestCardEntry> = emptyMap(),
)

@Serializable
private data class ManifestCardEntry(
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
