package com.boardbanker.app.gameplay.location

/**
 * Transient Location workflow context (GR-SAVE-002 — not persisted).
 *
 * After Location fee commits, holds the landing player until destination Property
 * is scanned and handed to the normal Property workflow.
 */
class LocationWorkflowHolder {
    var landingPlayerId: String? = null
        private set

    fun beginWaitingForDestination(playerId: String) {
        landingPlayerId = playerId
    }

    fun clear() {
        landingPlayerId = null
    }

    fun isWaitingForDestination(): Boolean = landingPlayerId != null
}
