package com.boardbanker.core.rules

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.PropertyDefinition
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.TemporaryEffect

object RentLevelOperations {

    fun clampLevel(level: Int, min: Int, max: Int): Int = level.coerceIn(min, max)

    fun increaseLevel(current: Int, delta: Int, max: Int): Int =
        clampLevel(current + delta, 1, max)

    fun decreaseLevel(current: Int, delta: Int, min: Int): Int =
        clampLevel(current - delta, min, Int.MAX_VALUE)

    fun rentAmount(
        definition: PropertyDefinition,
        propertyState: PropertyState,
        chargeLevelOverride: Int? = null,
    ): Int {
        val level = chargeLevelOverride ?: propertyState.currentRentLevel
        return definition.rentLevels.firstOrNull { it.level == level }?.amount
            ?: definition.rentLevels.first { it.level == 1 }.amount
    }

    fun effectiveChargeLevel(
        propertyState: PropertyState,
        temporaryEffects: List<TemporaryEffect>,
    ): Int? {
        val activeCap = temporaryEffects.firstOrNull {
            it.active && it.effectType == "FORCE_LEVEL_1_RENT" && it.remainingUses > 0
        }
        return if (activeCap != null) 1 else null
    }

    fun applyRentLevelChanges(
        properties: Map<String, PropertyState>,
        changes: Map<String, Int>,
    ): Map<String, PropertyState> {
        if (changes.isEmpty()) return properties
        return properties.mapValues { (id, state) ->
            val newLevel = changes[id]
            if (newLevel != null) state.copy(currentRentLevel = newLevel) else state
        }
    }
}
