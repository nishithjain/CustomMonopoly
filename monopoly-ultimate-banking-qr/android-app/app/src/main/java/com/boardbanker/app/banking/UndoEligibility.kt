package com.boardbanker.app.banking

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.UndoSupport
import com.boardbanker.core.transaction.TransactionFactory

class UndoEligibility(private val definitions: GameDefinitions) {
    private val undoSupport = UndoSupport(definitions, TransactionFactory())

    fun canUndo(session: GameSession): Boolean = undoSupport.canUndo(session)

    fun undoDescription(session: GameSession): String? {
        if (!canUndo(session)) return null
        val lastTx = session.transactions.lastOrNull { it.transactionType != TransactionType.UNDO }
            ?: return null
        return when (lastTx.transactionType) {
            TransactionType.PROPERTY_PURCHASE -> {
                val propertyName = lastTx.propertyId?.let { definitions.properties[it]?.name } ?: "property"
                val playerName = lastTx.playerId?.let { resolveName(session, it) } ?: "Player"
                "Property purchase:\n$playerName bought $propertyName."
            }
            TransactionType.RENT_PAYMENT -> {
                val from = lastTx.fromEntity?.let { resolveEntityName(session, it) } ?: "?"
                val to = lastTx.toEntity?.let { resolveEntityName(session, it) } ?: "?"
                "Rent payment:\n$from paid $to M${lastTx.amount ?: "?"}."
            }
            TransactionType.BANK_CREDIT -> {
                val playerName = lastTx.playerId?.let { resolveName(session, it) } ?: "Player"
                "GO salary:\n$playerName collected M${lastTx.amount ?: "?"}."
            }
            TransactionType.LOCATION_FEE -> {
                val playerName = lastTx.playerId?.let { resolveName(session, it) } ?: "Player"
                "Location fee:\n$playerName paid M${lastTx.amount ?: "?"}."
            }
            TransactionType.JAIL_STATUS_CHANGE -> {
                val playerName = lastTx.playerId?.let { resolveName(session, it) } ?: "Player"
                "Jail payment:\n$playerName paid to leave Jail."
            }
            else -> "Last action: ${lastTx.transactionType.name.replace('_', ' ')}."
        }
    }

    private fun resolveEntityName(session: GameSession, entity: String): String =
        if (entity == EntityRef.BANK) {
            "Bank"
        } else {
            PlayerDisplayNames.displayName(session, entity, definitions)
        }

    private fun resolveName(session: GameSession, playerId: String): String =
        PlayerDisplayNames.displayName(session, playerId, definitions)
}
