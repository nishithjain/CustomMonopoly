package com.boardbanker.app.scanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.CommonUiIconImage
import com.boardbanker.app.ui.screens.playerdetails.propertyColorGroupColor
import com.boardbanker.core.card.CardType

object RecognizedCardSummaryTestTags {
    const val SUMMARY = "recognized_card_summary"
    const val NAME = "recognized_card_summary_name"
    const val TYPE = "recognized_card_summary_type"
    const val CARD_ID = "recognized_card_summary_card_id"
    const val EDITION = "recognized_card_summary_edition"
}

private val SuccessGreen = Color(0xFF43A047)
private val EventAccent = Color(0xFF8E6B00)
private val EnergyGridAccent = Color(0xFF00796B)

@Composable
fun RecognizedCardSummary(
    cardType: CardType,
    cardId: String,
    displayName: String,
    editionName: String?,
    modifier: Modifier = Modifier,
    propertyColorGroup: String? = null,
) {
    val typeLabel = RecognizedCardSummaryPresentation.typeLabel(cardType)
    val typeIcon = RecognizedCardSummaryPresentation.typeIcon(cardType)
    val accentColor = recognizedCardAccentColor(cardType, propertyColorGroup)
    val resolvedName = displayName.takeIf { it.isNotBlank() } ?: cardId
    val summaryDescription = buildString {
        append("Card recognized. ")
        append(resolvedName)
        append(", ")
        append(typeLabel)
        append(". Card ID ")
        append(cardId)
        if (!editionName.isNullOrBlank()) {
            append(". Edition ")
            append(editionName)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(RecognizedCardSummaryTestTags.SUMMARY)
            .semantics {
                contentDescription = summaryDescription
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accentColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CommonUiIconImage(
                        icon = CommonUiIcon.CHECK,
                        size = 20.dp,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(SuccessGreen),
                    )
                    Text(
                        text = "Card recognized",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CommonUiIconImage(
                            icon = typeIcon,
                            size = 28.dp,
                            contentDescription = typeLabel,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = resolvedName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(RecognizedCardSummaryTestTags.NAME),
                        )
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(RecognizedCardSummaryTestTags.TYPE),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryMetadataRow(
                        label = "Card ID",
                        value = cardId,
                        valueTestTag = RecognizedCardSummaryTestTags.CARD_ID,
                    )
                    if (!editionName.isNullOrBlank()) {
                        SummaryMetadataRow(
                            label = "Edition",
                            value = editionName,
                            valueTestTag = RecognizedCardSummaryTestTags.EDITION,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetadataRow(
    label: String,
    value: String,
    valueTestTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(0.62f)
                .testTag(valueTestTag),
        )
    }
}

@Composable
private fun recognizedCardAccentColor(
    cardType: CardType,
    propertyColorGroup: String?,
): Color = when (cardType) {
    CardType.EVENT -> EventAccent
    CardType.ENERGY_GRID -> EnergyGridAccent
    CardType.PROPERTY -> propertyColorGroup
        ?.let { propertyColorGroupColor(it) }
        ?: MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primary
}
