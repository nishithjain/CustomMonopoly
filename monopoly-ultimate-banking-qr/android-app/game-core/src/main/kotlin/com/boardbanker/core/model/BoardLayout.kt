package com.boardbanker.core.model

import kotlinx.serialization.Serializable

enum class BoardSpaceType {
    GO,
    PROPERTY,
    EVENT,
    LOCATION,
    ENERGY_GRID,
    JAIL,
    FREE_PARKING,
    GO_TO_JAIL,
}

@Serializable
data class BoardSpace(
    val position: Int,
    val spaceId: String,
    val spaceType: BoardSpaceType,
    val targetId: String? = null,
    val deckId: String? = null,
)

@Serializable
data class BoardLayout(
    val schemaVersion: Int = 1,
    val spaces: List<BoardSpace>,
) {
    val size: Int get() = spaces.size

    val propertySpaces: List<BoardSpace> =
        spaces.filter { it.spaceType == BoardSpaceType.PROPERTY }

    val eventSpaces: List<BoardSpace> =
        spaces.filter { it.spaceType == BoardSpaceType.EVENT }

    val energyGridSpaces: List<BoardSpace> =
        spaces.filter { it.spaceType == BoardSpaceType.ENERGY_GRID }

    fun spaceAt(position: Int): BoardSpace? = spaces.firstOrNull { it.position == position }

    fun boardNumberForEnergyGrid(energyGridId: String): Int? =
        energyGridSpaces.firstOrNull { it.targetId == energyGridId }?.position
}
