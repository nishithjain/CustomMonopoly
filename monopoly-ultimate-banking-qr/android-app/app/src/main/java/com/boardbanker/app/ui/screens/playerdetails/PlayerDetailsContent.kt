package com.boardbanker.app.ui.screens.playerdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.util.pluralize

@Composable
fun PlayerDetailsAssetsContent(
    uiState: PlayerDetailsUiState,
    onPropertySelected: (String) -> Unit,
    onEnergyGridSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerSummaryCard(uiState = uiState)

        SectionHeader(
            title = "Owned Properties (${uiState.propertyCount})",
        )

        if (uiState.ownedProperties.isEmpty()) {
            EmptySectionMessage("No properties owned")
        } else {
            uiState.ownedProperties.forEach { property ->
                OwnedPropertyCard(
                    property = property,
                    onClick = { onPropertySelected(property.propertyId) },
                )
            }
        }

        if (uiState.hasEnergyGridsInEdition) {
            SectionHeader(
                title = "Owned Energy Grids (${uiState.energyGridCount})",
            )

            if (uiState.ownedEnergyGrids.isEmpty()) {
                EmptySectionMessage("No Energy Grids owned")
            } else {
                uiState.ownedEnergyGrids.forEach { energyGrid ->
                    OwnedEnergyGridCard(
                        energyGrid = energyGrid,
                        onClick = { onEnergyGridSelected(energyGrid.energyGridId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptySectionMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayerSummaryCard(uiState: PlayerDetailsUiState) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_details_summary_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                PlayerIdentity(
                    playerId = uiState.playerId,
                    playerName = uiState.playerName,
                    iconSize = PlayerIconSize.Large,
                )
                if (uiState.isActiveTurn) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Current Turn",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (uiState.tokenName.isNotBlank()) {
                Text(
                    text = uiState.tokenName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.balanceText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = uiState.playerStatusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (uiState.inJail) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryStat(
                    value = uiState.propertyCount.toString(),
                    label = pluralize(uiState.propertyCount, "Property"),
                    testTag = "summary_property_count",
                )
                if (uiState.hasEnergyGridsInEdition) {
                    SummaryStat(
                        value = uiState.energyGridCount.toString(),
                        label = pluralize(uiState.energyGridCount, "Energy Grid"),
                        testTag = "summary_energy_grid_count",
                    )
                }
                SummaryStat(
                    value = uiState.totalAssetCount.toString(),
                    label = "Total assets",
                    testTag = "summary_total_assets",
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, testTag: String? = null) {
    Column(
        modifier = Modifier.then(
            if (testTag != null) Modifier.testTag(testTag) else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun OwnedPropertyCard(
    property: OwnedPropertyUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("owned_property_card_${property.propertyId}")
            .semantics {
                contentDescription = "Property ${property.propertyName}"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(propertyColorGroupColor(property.colorGroup)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = property.propertyName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        color = propertyColorGroupColor(property.colorGroup).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = property.colorGroupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DetailRow(label = "Rent level", value = "${property.rentLevel} of ${property.maxRentLevel}")
                DetailRow(label = "Current rent", value = property.currentRentText)
                DetailRow(label = "Purchase price", value = property.purchasePriceText)
            }
        }
    }
}

@Composable
fun OwnedEnergyGridCard(
    energyGrid: OwnedEnergyGridUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("owned_energy_grid_card_${energyGrid.energyGridId}")
            .semantics {
                contentDescription = "Energy Grid ${energyGrid.energyGridName}"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = energyGrid.energyGridName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    energyGrid.boardPositionLabel?.let { position ->
                        Text(
                            text = position,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = energyGrid.categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            energyGrid.rentTierText?.let { tier ->
                Text(
                    text = tier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DetailRow(label = "Current rent", value = energyGrid.currentRentText)
            DetailRow(label = "Purchase price", value = energyGrid.purchasePriceText)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
