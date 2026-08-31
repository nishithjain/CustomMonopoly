package com.boardbanker.app.cards

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.boardbanker.core.card.CardType
import java.util.concurrent.ConcurrentHashMap

class CardFrontImageProvider private constructor(
    private val registry: CardFrontRegistry,
    private val appContext: Context,
) {
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()

    fun resolve(
        editionId: String,
        cardType: CardType,
        cardId: String,
    ): CardFrontResolveResult = registry.resolve(editionId, cardType, cardId)

    fun loadImageBitmap(
        editionId: String,
        cardType: CardType,
        cardId: String,
    ): ImageBitmap? {
        val resolved = resolve(editionId, cardType, cardId)
        if (resolved !is CardFrontResolveResult.Found) {
            return null
        }
        val cacheKey = CardFrontLookupKey(editionId, cardType, cardId).cacheKey()
        return bitmapCache.getOrPut(cacheKey) {
            appContext.assets.open(resolved.image.assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)!!.asImageBitmap()
            }
        }
    }

    @Composable
    fun rememberFrontImageBitmap(
        editionId: String?,
        cardType: CardType?,
        cardId: String?,
    ): ImageBitmap? {
        val rememberedEditionId = editionId ?: return null
        val rememberedCardType = cardType ?: return null
        val rememberedCardId = cardId ?: return null
        return remember(rememberedEditionId, rememberedCardType, rememberedCardId) {
            loadImageBitmap(rememberedEditionId, rememberedCardType, rememberedCardId)
        }
    }

    companion object {
        @Volatile
        private var instance: CardFrontImageProvider? = null

        fun getInstance(context: Context): CardFrontImageProvider {
            return instance ?: synchronized(this) {
                instance ?: CardFrontImageProvider(
                    registry = CardFrontRegistry.load(context.applicationContext),
                    appContext = context.applicationContext,
                ).also { instance = it }
            }
        }
    }
}
