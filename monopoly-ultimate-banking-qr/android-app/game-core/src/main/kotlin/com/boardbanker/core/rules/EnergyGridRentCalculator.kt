package com.boardbanker.core.rules

import com.boardbanker.core.model.EnergyGridDefinition
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

object EnergyGridRentCalculator {
    fun ownedCount(session: GameSession, ownerId: String): Int =
        session.energyGrids.values.count { it.ownerPlayerId == ownerId }

    fun rentAmount(definitions: GameDefinitions, session: GameSession, ownerId: String): Int {
        val count = ownedCount(session, ownerId).coerceAtLeast(1)
        return rentForOwnedCount(definitions.energyGrids.values.firstOrNull()?.rentLevels ?: emptyList(), count)
            ?: definitions.energyGrids.values
                .firstOrNull()
                ?.rentLevels
                ?.maxByOrNull { it.ownedCount }
                ?.amount
            ?: 0
    }

    fun rentForOwnedCount(rentLevels: List<com.boardbanker.core.model.EnergyGridRentLevel>, ownedCount: Int): Int? {
        val sorted = rentLevels.sortedBy { it.ownedCount }
        return sorted.lastOrNull { it.ownedCount <= ownedCount }?.amount
    }

    fun rentForOwner(definitions: GameDefinitions, session: GameSession, ownerId: String): Int {
        val count = ownedCount(session, ownerId)
        if (count <= 0) return 0
        val sample = definitions.energyGrids.values.firstOrNull()?.rentLevels ?: return 0
        return rentForOwnedCount(sample, count) ?: 0
    }

    fun validateRentTable(grids: Collection<EnergyGridDefinition>): List<String> {
        if (grids.isEmpty()) return emptyList()
        val sample = grids.first().rentLevels.sortedBy { it.ownedCount }
        val problems = mutableListOf<String>()
        val counts = sample.map { it.ownedCount }
        if (counts != counts.distinct()) {
            problems += "Energy grid rent ownedCount values must be unique"
        }
        if (counts.isEmpty() || counts.first() != 1 || counts != (1..counts.size).toList()) {
            problems += "Energy grid rent ownedCount values must be contiguous starting at 1"
        }
        if (sample.any { it.amount < 0 }) {
            problems += "Energy grid rent amounts must be non-negative"
        }
        if (sample.zipWithNext().any { (a, b) -> a.amount > b.amount }) {
            problems += "Energy grid rent amounts must be non-decreasing by ownedCount"
        }
        for (grid in grids) {
            if (grid.rentLevels.sortedBy { it.ownedCount } != sample) {
                problems += "Energy grid '${grid.energyGridId}' rent table does not match edition rent table"
            }
        }
        return problems
    }
}
