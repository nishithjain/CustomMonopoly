package com.boardbanker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boardbanker.app.cards.CardFrontImageProvider
import com.boardbanker.app.cards.CardFrontResolveResult
import com.boardbanker.core.card.CardType

@Composable
fun CardFrontImage(
    editionId: String,
    cardType: CardType,
    cardId: String,
    modifier: Modifier = Modifier,
    provider: CardFrontImageProvider = CardFrontImageProvider.getInstance(LocalContext.current),
) {
    val resolved = remember(editionId, cardType, cardId) {
        provider.resolve(editionId, cardType, cardId)
    }
    when (resolved) {
        is CardFrontResolveResult.Found -> {
            val front = resolved.image
            val bitmap = provider.rememberFrontImageBitmap(editionId, cardType, cardId) ?: return
            val aspectRatio = front.takeIf { it.width > 0 && it.height > 0 }?.let {
                it.width.toFloat() / it.height.toFloat()
            }
            val isLandscape = front.orientation == "LANDSCAPE"
            Image(
                bitmap = bitmap,
                contentDescription = "Card front for $cardId",
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
        is CardFrontResolveResult.Missing -> {
            MissingCardFrontPlaceholder(
                editionId = resolved.editionId,
                cardType = resolved.cardType,
                cardId = resolved.cardId,
                reason = resolved.reason,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MissingCardFrontPlaceholder(
    editionId: String,
    cardType: CardType,
    cardId: String,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Artwork unavailable\n$editionId / ${cardType.name} / $cardId\n$reason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
