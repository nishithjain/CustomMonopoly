package com.boardbanker.core.event

import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

/** Resolves which property is sold during [FORCED_PROPERTY_SELLBACK] (Eminent Domain). */
object ForcedPropertySellbackSelection {
    data class Result(
        val ownedPropertyIds: List<String>,
        val lowestValueCandidates: List<String>,
        val autoSelectedPropertyId: String?,
        val requiresPropertyScan: Boolean,
    )

    fun resolve(
        session: GameSession,
        definitions: GameDefinitions,
        actingPlayerId: String,
    ): Result {
        val owned = session.properties.filter { it.value.ownerPlayerId == actingPlayerId }
        if (owned.isEmpty()) {
            return Result(
                ownedPropertyIds = emptyList(),
                lowestValueCandidates = emptyList(),
                autoSelectedPropertyId = null,
                requiresPropertyScan = false,
            )
        }
        val ownedIds = owned.keys.toList()
        val minPrice = owned.minOf { definitions.properties[it.key]!!.purchasePrice }
        val candidates = owned
            .filter { definitions.properties[it.key]!!.purchasePrice == minPrice }
            .keys
            .sorted()
        return Result(
            ownedPropertyIds = ownedIds,
            lowestValueCandidates = candidates,
            autoSelectedPropertyId = candidates.singleOrNull(),
            requiresPropertyScan = candidates.size > 1,
        )
    }

    fun missingPropertyMessage(
        session: GameSession,
        definitions: GameDefinitions,
        actingPlayerId: String,
        propertyId: String?,
    ): String? {
        val selection = resolve(session, definitions, actingPlayerId)
        if (selection.ownedPropertyIds.isEmpty()) return null
        if (!selection.requiresPropertyScan) return null
        if (propertyId == null) return "Select one of your lowest-value properties"
        if (propertyId !in selection.lowestValueCandidates) {
            return "Select one of your lowest-value properties"
        }
        return null
    }
}
