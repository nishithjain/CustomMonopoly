package com.boardbanker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boardbanker.app.cards.CardFrontImageProvider

@Composable
fun CardFrontImage(
    cardId: String?,
    modifier: Modifier = Modifier,
    provider: CardFrontImageProvider = CardFrontImageProvider.getInstance(LocalContext.current),
) {
    val front = remember(cardId) { cardId?.let { provider.getFrontImage(it) } }
    val bitmap = provider.rememberFrontImageBitmap(cardId) ?: return
    val aspectRatio = front?.takeIf { it.width > 0 && it.height > 0 }?.let {
        it.width.toFloat() / it.height.toFloat()
    }
    val isLandscape = front?.orientation == "LANDSCAPE"
    Image(
        bitmap = bitmap,
        contentDescription = cardId?.let { "Card front for $it" },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (aspectRatio != null) {
                    Modifier.aspectRatio(aspectRatio)
                } else if (isLandscape) {
                    Modifier.heightIn(max = 240.dp)
                } else {
                    Modifier.heightIn(max = 420.dp)
                },
            ),
        contentScale = ContentScale.Fit,
    )
}
