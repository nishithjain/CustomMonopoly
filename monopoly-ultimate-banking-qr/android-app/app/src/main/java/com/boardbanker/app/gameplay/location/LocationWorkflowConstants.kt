package com.boardbanker.app.gameplay.location

/**
 * Sentinel property id for [com.boardbanker.core.command.GameCommand.PayLocationFee]
 * when only the M100 location movement fee should be committed.
 *
 * The property id is not present in [com.boardbanker.core.model.GameSession.properties],
 * so the Game Engine deducts the fee without applying destination landing rules.
 */
object LocationWorkflowConstants {
    const val FEE_ONLY_PROPERTY_ID = "__LOCATION_FEE_PENDING__"
}
