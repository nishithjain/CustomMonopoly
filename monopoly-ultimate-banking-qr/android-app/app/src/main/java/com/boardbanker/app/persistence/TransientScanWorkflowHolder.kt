package com.boardbanker.app.persistence

/**
 * In-memory scanner workflow state. Intentionally not persisted (GR-SAVE-002).
 */
class TransientScanWorkflowHolder {
    var workflowState: ScanWorkflowState = ScanWorkflowState.READY
        private set

    fun enterWaitingForPlayer() {
        workflowState = ScanWorkflowState.WAITING_FOR_PLAYER
    }

    fun enterWaitingForProperty() {
        workflowState = ScanWorkflowState.WAITING_FOR_PROPERTY
    }

    fun enterEventIdentified() {
        workflowState = ScanWorkflowState.EVENT_IDENTIFIED
    }

    fun resetToReady() {
        workflowState = ScanWorkflowState.READY
    }
}

enum class ScanWorkflowState {
    READY,
    WAITING_FOR_PLAYER,
    WAITING_FOR_PROPERTY,
    PROPERTY_IDENTIFIED,
    EVENT_IDENTIFIED,
}
