package com.boardbanker.core.rules

import com.boardbanker.core.model.BoardLayout
import com.boardbanker.core.model.BoardSpaceType
import com.boardbanker.core.model.GameDefinitions

object BoardTraversal {
    fun nextEnergyGridSpace(
        definitions: GameDefinitions,
        fromPosition: Int,
    ): com.boardbanker.core.model.BoardSpace? {
        val layout = definitions.boardLayout
        if (layout.energyGridSpaces.isEmpty()) return null
        val size = layout.size
        for (offset in 1..size) {
            val position = (fromPosition + offset) % size
            val space = layout.spaceAt(position)
            if (space?.spaceType == BoardSpaceType.ENERGY_GRID) {
                return space
            }
        }
        return null
    }

    fun energyGridSpaceIds(layout: BoardLayout): List<String> =
        layout.energyGridSpaces.mapNotNull { it.targetId }

    fun passedGoOnForwardMove(fromPosition: Int, toPosition: Int): Boolean =
        toPosition <= fromPosition
}
