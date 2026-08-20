package com.boardbanker.app.cards

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

class CardFrontImageProvider private constructor(
    private val registry: CardFrontRegistry,
    private val appContext: Context,
) {
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()

    fun getFrontImage(cardId: String): CardFrontImage? = registry.getFrontImage(cardId)

    fun loadImageBitmap(cardId: String): ImageBitmap? {
        val front = registry.getFrontImage(cardId) ?: return null
        return bitmapCache.getOrPut(front.cardId) {
            appContext.assets.open(front.assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)!!.asImageBitmap()
            }
        }
    }

    @Composable
    fun rememberFrontImageBitmap(cardId: String?): ImageBitmap? {
        val rememberedId = cardId ?: return null
        return remember(rememberedId) {
            loadImageBitmap(rememberedId)
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
