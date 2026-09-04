package com.boardbanker.app.cards

import android.content.Context
import com.boardbanker.core.card.CardType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CardFrontRegistry private constructor(
    private val commonUserCards: Map<String, CardFrontDefinition>,
    private val editionCards: Map<CardFrontLookupKey, CardFrontDefinition>,
) {
    fun resolve(
        editionId: String,
        cardType: CardType,
        cardId: String,
    ): CardFrontResolveResult {
        val normalizedEditionId = editionId.trim()
        val normalizedCardId = cardId.trim()
        if (normalizedEditionId.isEmpty()) {
            return missing(normalizedEditionId, cardType, normalizedCardId, "editionId is blank")
        }
        if (normalizedCardId.isEmpty()) {
            return missing(normalizedEditionId, cardType, normalizedCardId, "cardId is blank")
        }

        return when (cardType) {
            CardType.USER -> {
                val definition = commonUserCards[normalizedCardId]
                    ?: return missing(
                        normalizedEditionId,
                        cardType,
                        normalizedCardId,
                        "common user card front is not registered",
                    )
                found(definition)
            }
            CardType.PROPERTY,
            CardType.EVENT,
            CardType.ENERGY_GRID,
            -> {
                val key = CardFrontLookupKey(normalizedEditionId, cardType, normalizedCardId)
                val definition = editionCards[key]
                    ?: return missing(
                        normalizedEditionId,
                        cardType,
                        normalizedCardId,
                        "no ${cardType.name.lowercase()} front for edition '$normalizedEditionId'",
                    )
                found(definition)
            }
        }
    }

    private fun found(definition: CardFrontDefinition): CardFrontResolveResult.Found =
        CardFrontResolveResult.Found(
            CardFrontImage(
                editionId = definition.editionId,
                cardId = definition.cardId,
                cardType = definition.cardType,
                assetPath = definition.asset,
                orientation = definition.orientation,
                width = definition.width,
                height = definition.height,
            ),
        )

    private fun missing(
        editionId: String,
        cardType: CardType,
        cardId: String,
        reason: String,
    ): CardFrontResolveResult.Missing =
        CardFrontResolveResult.Missing(
            editionId = editionId,
            cardType = cardType,
            cardId = cardId,
            reason = reason,
        )

    companion object {
        private const val COMMON_MANIFEST_ASSET = "cards/common/android_card_front_manifest.json"

        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun load(context: Context): CardFrontRegistry {
            val assetManager = context.assets
            return loadFromAssetRoot(
                readText = { path -> assetManager.open(path).bufferedReader().use { it.readText() } },
                listChildDirectories = { path -> assetManager.list(path)?.toList().orEmpty() },
            )
        }

        internal fun loadFromAssetsDirectory(assetsRoot: java.nio.file.Path): CardFrontRegistry =
            loadFromAssetRoot(
                readText = { relative -> assetsRoot.resolve(relative).toFile().readText() },
                listChildDirectories = { relative ->
                    val directory = assetsRoot.resolve(relative)
                    if (!directory.toFile().isDirectory) {
                        emptyList()
                    } else {
                        directory.toFile().listFiles()
                            ?.filter { it.isDirectory }
                            ?.map { it.name }
                            .orEmpty()
                    }
                },
            )

        private fun loadFromAssetRoot(
            readText: (String) -> String,
            listChildDirectories: (String) -> List<String>,
        ): CardFrontRegistry {
            val commonUserCards = loadManifestEntries(
                readText(COMMON_MANIFEST_ASSET),
                editionId = "common",
            ).associateBy { it.cardId }

            val editionCards = mutableMapOf<CardFrontLookupKey, CardFrontDefinition>()
            val editionRoot = "cards/editions"
            listChildDirectories(editionRoot).forEach { editionId ->
                val manifestPath = "$editionRoot/$editionId/android_card_front_manifest.json"
                if (!canRead(readText, manifestPath)) {
                    return@forEach
                }
                val entries = loadManifestEntries(readText(manifestPath), editionId = editionId)
                entries.forEach { definition ->
                    val cardType = CardType.valueOf(definition.cardType)
                    editionCards[CardFrontLookupKey(editionId, cardType, definition.cardId)] = definition
                }
            }
            return CardFrontRegistry(commonUserCards, editionCards)
        }

        private fun canRead(readText: (String) -> String, path: String): Boolean =
            runCatching { readText(path) }.isSuccess

        private fun loadManifestEntries(payload: String, editionId: String): List<CardFrontDefinition> {
            val manifest = json.decodeFromString(ManifestPayload.serializer(), payload)
            require(manifest.editionId == editionId || (editionId == "common" && manifest.editionId == "common")) {
                "Manifest editionId '${manifest.editionId}' does not match expected '$editionId'"
            }
            return manifest.cards.map { (_, entry) ->
                CardFrontDefinition(
                    editionId = manifest.editionId,
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
        }
    }
}

@Serializable
private data class ManifestPayload(
    val schemaVersion: Int = 2,
    val editionId: String,
    val generatedBy: String = "",
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
