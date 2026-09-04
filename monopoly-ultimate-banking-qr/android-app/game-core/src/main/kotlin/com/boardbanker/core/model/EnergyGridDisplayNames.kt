package com.boardbanker.core.model

object EnergyGridDisplayNames {
    fun displayNameWithNumber(grid: EnergyGridDefinition, definitions: GameDefinitions): String {
        val boardNumber = definitions.boardLayout.boardNumberForEnergyGrid(grid.energyGridId)
        return if (boardNumber != null) "[$boardNumber] ${grid.name}" else grid.name
    }

    fun displayNameWithNumber(energyGridId: String, definitions: GameDefinitions): String {
        val grid = definitions.energyGrids[energyGridId]
        return when {
            grid != null -> displayNameWithNumber(grid, definitions)
            else -> energyGridId
        }
    }
}

fun EnergyGridDefinition.displayNameWithNumber(definitions: GameDefinitions): String =
    EnergyGridDisplayNames.displayNameWithNumber(this, definitions)
